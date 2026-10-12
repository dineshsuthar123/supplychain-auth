package com.lotline.audit;

import com.lotline.identity.Member;
import com.lotline.recall.RecallService;
import com.lotline.shared.*;
import com.lotline.workspace.Scope;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ObservationService {
  public record Observation(
      @NotNull UUID lotId,
      @NotBlank String quantity,
      @NotBlank @Size(max = 1000) String reason,
      @NotBlank @Size(max = 1000) String evidence,
      String occurredAt) {}

  public record Resolution(
      @NotBlank @Size(max = 1000) String reason, @NotBlank @Size(max = 1000) String evidence) {}

  private final Db db;
  private final CommandBus bus;
  private final Scope scope;
  private final RecallService recalls;

  public ObservationService(Db db, CommandBus bus, Scope scope, RecallService recalls) {
    this.db = db;
    this.bus = bus;
    this.scope = scope;
    this.recalls = recalls;
  }

  public Object list(Member p) {
    p.manageRecall();
    return db.rows(
        "SELECT o.*,EXISTS(SELECT 1 FROM observation_resolutions r WHERE"
            + " r.workspace_id=o.workspace_id AND r.observation_id=o.id) resolved FROM"
            + " custody_observations o WHERE o.workspace_id=? ORDER BY sequence DESC LIMIT 500",
        p.workspaceId());
  }

  public Map<String, Object> report(Member p, String key, Observation r) {
    p.manageRecall();
    return bus.run(
        p,
        "CUSTODY_GAP_OBSERVED",
        key,
        r,
        r.occurredAt(),
        c -> {
          scope.lot(p, r.lotId());
          UUID id = UUID.randomUUID();
          db.update(
              "INSERT INTO custody_observations VALUES(?,?,?,?,?,?,?,?)",
              p.workspaceId(),
              id,
              r.lotId(),
              Quantities.parse(r.quantity()),
              r.reason(),
              r.evidence(),
              c.sequence(),
              p.userId());
          recalls.newHoldings(
              c,
              r.lotId(),
              "New observation reports unknown custody. Review the gap; it does not commission"
                  + " stock.");
          return Map.of("id", id.toString());
        });
  }

  public Map<String, Object> resolve(Member p, UUID id, String key, Resolution r) {
    p.manageRecall();
    return bus.run(
        p,
        "CUSTODY_GAP_RESOLVED:" + id,
        key,
        r,
        null,
        c -> {
          db.one(
              "SELECT id FROM custody_observations WHERE workspace_id=? AND id=?",
              p.workspaceId(),
              id);
          if (db.count(
                  "SELECT count(*) FROM observation_resolutions WHERE workspace_id=? AND"
                      + " observation_id=?",
                  p.workspaceId(),
                  id)
              > 0)
            throw new DomainError(
                409, "GAP_RESOLVED", "This evidence gap already has a recorded resolution.");
          db.update(
              "INSERT INTO observation_resolutions VALUES(?,?,?,?,?,?,?)",
              p.workspaceId(),
              UUID.randomUUID(),
              id,
              r.reason(),
              r.evidence(),
              c.sequence(),
              p.userId());
          return Map.of("id", id.toString());
        });
  }
}
