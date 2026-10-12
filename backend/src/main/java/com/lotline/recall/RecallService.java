package com.lotline.recall;

import com.lotline.identity.Member;
import com.lotline.inventory.Ledger;
import com.lotline.shared.*;
import com.lotline.shared.CommandBus.Command;
import com.lotline.workspace.Scope;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class RecallService {
  public record Preview(@NotEmpty @Size(max = 30) List<UUID> roots) {}

  public record Activate(
      @NotEmpty @Size(max = 30) List<UUID> roots,
      @NotBlank @Size(max = 160) String title,
      @NotBlank @Size(max = 1000) String reason,
      long previewSequence,
      boolean confirmed) {}

  public record Ack(@NotBlank @Size(max = 1000) String note) {}

  public record Decision(@NotBlank @Size(max = 1000) String reason) {}

  private final Db db;
  private final CommandBus bus;
  private final Ledger ledger;
  private final Scope scope;
  private final Json json;

  public RecallService(Db db, CommandBus bus, Ledger ledger, Scope scope, Json json) {
    this.db = db;
    this.bus = bus;
    this.ledger = ledger;
    this.scope = scope;
    this.json = json;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Map<String, Object> preview(Member p, Preview r) {
    p.manageRecall();
    return calculate(
        p, r.roots(), db.count("SELECT sequence FROM workspaces WHERE id=?", p.workspaceId()));
  }

  public Map<String, Object> calculate(Member p, List<UUID> roots, long seq) {
    Set<String> units = new HashSet<>();
    for (UUID root : roots) {
      var lot =
          db.one(
              "SELECT p.base_unit FROM lots l JOIN products p ON p.workspace_id=l.workspace_id AND"
                  + " p.id=l.product_id WHERE l.workspace_id=? AND l.id=?",
              p.workspaceId(),
              root);
      units.add(lot.get("base_unit").toString());
    }
    if (units.size() != 1)
      throw DomainError.invalid(
          "INCOMPATIBLE_RECALL_UNITS",
          "Review roots with the same base unit in one incident. Open separate incidents for"
              + " incompatible units.");
    Set<UUID> affected = new LinkedHashSet<>();
    Map<UUID, List<Map<String, Object>>> paths = new LinkedHashMap<>();
    for (UUID id : roots) {
      scope.lot(p, id);
      affected.add(id);
      paths.put(id, new ArrayList<>());
    }
    var edges =
        db.rows(
            "SELECT input_lot_id,output_lot_id,operation_id FROM lineage WHERE workspace_id=? AND"
                + " sequence<=? ORDER BY sequence,input_lot_id,output_lot_id LIMIT 10001",
            p.workspaceId(),
            seq);
    Map<UUID, List<Map<String, Object>>> adjacent = new HashMap<>();
    for (var e : edges)
      adjacent.computeIfAbsent(db.uuid(e.get("input_lot_id")), x -> new ArrayList<>()).add(e);
    Deque<UUID> q = new ArrayDeque<>(affected);
    boolean complete = edges.size() <= 10000;
    while (!q.isEmpty()) {
      UUID input = q.removeFirst();
      for (var e : adjacent.getOrDefault(input, List.of())) {
        UUID out = db.uuid(e.get("output_lot_id"));
        if (affected.add(out)) {
          var path = new ArrayList<>(paths.get(input));
          path.add(e);
          paths.put(out, path);
          q.addLast(out);
          if (affected.size() > 2000) {
            complete = false;
            q.clear();
            break;
          }
        }
      }
    }
    var holdings =
        db.rows(
            "SELECT b.lot_id,l.code,p.base_unit,b.site_id,s.name site_name,s.organization_id,o.name"
                + " organization_name,b.status,b.transfer_id,b.quantity FROM balances b JOIN lots l"
                + " ON l.workspace_id=b.workspace_id AND l.id=b.lot_id JOIN products p ON"
                + " p.workspace_id=l.workspace_id AND p.id=l.product_id JOIN sites s ON"
                + " s.workspace_id=b.workspace_id AND s.id=b.site_id JOIN organizations o ON"
                + " o.workspace_id=s.workspace_id AND o.id=s.organization_id WHERE b.workspace_id=?"
                + " AND b.quantity>0 AND b.status<>'CONSUMED' ORDER BY l.code,s.name,b.status LIMIT"
                + " 10001",
            p.workspaceId());
    if (holdings.size() > 10000) complete = false;
    List<Map<String, Object>> exposure = new ArrayList<>(), unaffected = new ArrayList<>();
    Map<String, BigDecimal> sums = new LinkedHashMap<>();
    for (String k :
        List.of(
            "recoverable",
            "inTransit",
            "unresolved",
            "recovered",
            "disposed",
            "accountableLoss",
            "unaffected")) sums.put(k, BigDecimal.ZERO);
    for (var h : holdings) {
      BigDecimal n = new BigDecimal(h.get("quantity").toString());
      if (affected.contains(db.uuid(h.get("lot_id")))) {
        var row = new LinkedHashMap<>(h);
        row.put("path", paths.get(db.uuid(h.get("lot_id"))));
        row.put("classification", "potentially affected");
        exposure.add(row);
        String k =
            switch (h.get("status").toString()) {
              case "AVAILABLE", "RESERVED", "HELD" -> "recoverable";
              case "IN_TRANSIT" -> "inTransit";
              case "UNRESOLVED" -> "unresolved";
              case "RECOVERED" -> "recovered";
              case "DISPOSED" -> "disposed";
              case "LOST" -> "accountableLoss";
              default -> "unresolved";
            };
        sums.put(k, sums.get(k).add(n));
      } else {
        unaffected.add(h);
        if (units.contains(h.get("base_unit").toString())
            && Set.of("AVAILABLE", "RESERVED", "HELD", "IN_TRANSIT", "UNRESOLVED")
                .contains(h.get("status"))) sums.put("unaffected", sums.get("unaffected").add(n));
      }
    }
    Map<String, String> totals = new LinkedHashMap<>();
    sums.forEach((k, v) -> totals.put(k, Quantities.text(v)));
    List<Map<String, Object>> lots = new ArrayList<>();
    for (UUID id : affected) lots.add(Map.of("lotId", id.toString(), "path", paths.get(id)));
    List<Map<String, Object>> gaps = new ArrayList<>();
    if (sums.get("unresolved").signum() > 0)
      gaps.add(
          Map.of(
              "kind",
              "UNRESOLVED_CUSTODY",
              "quantity",
              totals.get("unresolved"),
              "message",
              "The dispatched remainder has no accepted destination or accountable disposition."));
    var observations =
        db.rows(
            "SELECT o.id,o.lot_id,o.quantity,o.reason,o.evidence FROM custody_observations o WHERE"
                + " o.workspace_id=? AND o.sequence<=? AND NOT EXISTS(SELECT 1 FROM"
                + " observation_resolutions r WHERE r.workspace_id=o.workspace_id AND"
                + " r.observation_id=o.id AND r.sequence<=?)",
            p.workspaceId(),
            seq,
            seq);
    for (var o : observations)
      if (affected.contains(db.uuid(o.get("lot_id"))))
        gaps.add(
            Map.of(
                "kind",
                "UNKNOWN_CUSTODY_OBSERVATION",
                "quantity",
                o.get("quantity"),
                "message",
                o.get("reason"),
                "observationId",
                o.get("id")));
    var result = new LinkedHashMap<String, Object>();
    result.put("sequence", seq);
    result.put("complete", complete);
    result.put("baseUnit", units.iterator().next());
    result.put(
        "coverage",
        "Accepted records in this workspace; unreported physical events are unknown. Observations"
            + " are evidence gaps, not extra commissioned inventory.");
    result.put("roots", roots.stream().map(UUID::toString).toList());
    result.put("affectedLots", lots);
    result.put("exposure", exposure);
    result.put("unaffected", unaffected);
    result.put("totals", totals);
    result.put("gaps", gaps);
    return result;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> activate(Member p, String key, Activate r) {
    p.manageRecall();
    return bus.run(
        p,
        "RECALL_ACTIVATED",
        key,
        r,
        null,
        c -> {
          Map<String, Object> preview = calculate(p, r.roots(), c.sequence() - 1);
          if (r.previewSequence() != c.sequence() - 1)
            throw new DomainError(
                409,
                "STALE_PREVIEW",
                "The workspace changed after preview. Review the updated scope and confirm again.",
                preview);
          if (!Boolean.TRUE.equals(preview.get("complete")))
            throw DomainError.invalid(
                "INCOMPLETE_SCOPE",
                "Trace exceeded its bounds. Activation is blocked until the complete scope can be"
                    + " reviewed.");
          if (!r.confirmed())
            throw DomainError.invalid(
                "CONFIRM_SCOPE",
                "Review affected sites, unresolved quantities and the sequence, then deliberately"
                    + " confirm activation.");
          UUID recall = UUID.randomUUID();
          db.jdbc.update(
              con -> {
                var ps =
                    con.prepareStatement(
                        "INSERT INTO"
                            + " recalls(workspace_id,id,title,reason,roots,status,activation_sequence)"
                            + " VALUES(?,?,?,?,?,'ACTIVE',?)");
                ps.setObject(1, p.workspaceId());
                ps.setObject(2, recall);
                ps.setString(3, r.title());
                ps.setString(4, r.reason());
                ps.setArray(5, con.createArrayOf("uuid", r.roots().toArray()));
                ps.setLong(6, c.sequence());
                return ps;
              });
          for (var a : (List<Map<String, Object>>) preview.get("affectedLots"))
            db.update(
                "INSERT INTO recall_lots VALUES(?,?,?)",
                p.workspaceId(),
                recall,
                UUID.fromString(a.get("lotId").toString()));
          hold(c, recall);
          refreshTargets(c, recall);
          revision(c, recall, "Activation: " + r.reason());
          notifyTargets(
              c,
              recall,
              "Recall activated: "
                  + r.title()
                  + ". Acknowledge and account for your affected stock.");
          return Map.of("id", recall.toString(), "status", "ACTIVE");
        });
  }

  private void hold(Command c, UUID recall) {
    var rows =
        db.rows(
            "SELECT b.* FROM balances b JOIN recall_lots rl ON rl.workspace_id=b.workspace_id AND"
                + " rl.lot_id=b.lot_id WHERE rl.workspace_id=? AND rl.recall_id=? AND b.status IN"
                + " ('AVAILABLE','RESERVED') AND b.quantity>0 ORDER BY"
                + " b.lot_id,b.site_id,b.status,b.transfer_id",
            c.member().workspaceId(),
            recall);
    for (var h : rows)
      ledger.move(
          c,
          db.uuid(h.get("lot_id")),
          db.uuid(h.get("site_id")),
          h.get("status").toString(),
          h.get("transfer_id") == null ? null : db.uuid(h.get("transfer_id")),
          db.uuid(h.get("site_id")),
          "HELD",
          null,
          new BigDecimal(h.get("quantity").toString()));
  }

  private void refreshTargets(Command c, UUID recall) {
    UUID w = c.member().workspaceId();
    db.update(
        "UPDATE recall_targets SET quantity=0 WHERE workspace_id=? AND recall_id=?", w, recall);
    db.update(
        "INSERT INTO recall_targets(workspace_id,id,recall_id,lot_id,site_id,transfer_id,quantity)"
            + " SELECT"
            + " b.workspace_id,gen_random_uuid(),?,b.lot_id,b.site_id,b.transfer_id,sum(b.quantity)"
            + " FROM balances b JOIN recall_lots rl ON rl.workspace_id=b.workspace_id AND"
            + " rl.lot_id=b.lot_id WHERE rl.workspace_id=? AND rl.recall_id=? AND b.status IN"
            + " ('AVAILABLE','RESERVED','HELD','IN_TRANSIT','UNRESOLVED','RECOVERED','DISPOSED','LOST')"
            + " GROUP BY b.workspace_id,b.lot_id,b.site_id,b.transfer_id HAVING sum(b.quantity)>0"
            + " ON CONFLICT(workspace_id,recall_id,lot_id,site_id,transfer_id) DO UPDATE SET"
            + " quantity=excluded.quantity",
        recall,
        w,
        recall);
  }

  private void revision(Command c, UUID recall, String reason) {
    UUID w = c.member().workspaceId();
    long version = db.count("SELECT version FROM recalls WHERE workspace_id=? AND id=?", w, recall);
    var snapshot =
        Map.of(
            "targets",
            db.rows(
                "SELECT * FROM recall_targets WHERE workspace_id=? AND recall_id=? ORDER BY"
                    + " lot_id,site_id",
                w,
                recall),
            "lots",
            db.rows(
                "SELECT lot_id FROM recall_lots WHERE workspace_id=? AND recall_id=? ORDER BY"
                    + " lot_id",
                w,
                recall));
    db.update(
        "INSERT INTO recall_revisions VALUES(?,?,?,?,?::jsonb,?)",
        w,
        recall,
        version,
        c.sequence(),
        json.write(snapshot),
        reason);
  }

  private void notifyTargets(Command c, UUID recall, String message) {
    db.rows(
            "SELECT DISTINCT s.organization_id,s.id site_id FROM recall_targets t JOIN sites s ON"
                + " s.workspace_id=t.workspace_id AND s.id=t.site_id WHERE t.workspace_id=? AND"
                + " t.recall_id=? AND t.quantity>0",
            c.member().workspaceId(),
            recall)
        .forEach(
            x -> {
              bus.site(c, db.uuid(x.get("site_id")));
              bus.notify(c, db.uuid(x.get("organization_id")), message);
            });
  }

  public void newHoldings(Command c, UUID lot, String reason) {
    UUID w = c.member().workspaceId();
    var recalls =
        db.rows(
            "SELECT r.id,r.status,r.title FROM recalls r JOIN recall_lots rl ON"
                + " rl.workspace_id=r.workspace_id AND rl.recall_id=r.id WHERE r.workspace_id=? AND"
                + " rl.lot_id=? ORDER BY r.id",
            w,
            lot);
    for (var r : recalls) {
      UUID id = db.uuid(r.get("id"));
      db.update(
          "UPDATE recalls SET status='ACTIVE',closed_sequence=NULL,version=version+1 WHERE"
              + " workspace_id=? AND id=?",
          w,
          id);
      hold(c, id);
      refreshTargets(c, id);
      revision(
          c, id, reason + ("CLOSED".equals(r.get("status")) ? "; closed incident reopened" : ""));
      notifyTargets(c, id, "Recall scope revised: " + r.get("title") + ". " + reason);
    }
  }

  public List<Map<String, Object>> list(Member p) {
    return db.rows(
        "SELECT r.id,r.title,r.reason,r.status,r.version,r.activation_sequence FROM recalls r WHERE"
            + " r.workspace_id=? AND ("
            + (p.network()
                ? "true"
                : "EXISTS(SELECT 1 FROM recall_targets rt JOIN sites s ON"
                    + " s.workspace_id=rt.workspace_id AND s.id=rt.site_id WHERE"
                    + " rt.workspace_id=r.workspace_id AND rt.recall_id=r.id AND "
                    + scope.sitePredicate(p, "s")
                    + ")")
            + ") ORDER BY r.activation_sequence DESC LIMIT 200",
        p.workspaceId());
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Map<String, Object> detail(Member p, UUID id) {
    var recall = db.one("SELECT * FROM recalls WHERE workspace_id=? AND id=?", p.workspaceId(), id);
    recall.put(
        "base_unit",
        db.one(
                "SELECT p.base_unit FROM recalls r JOIN lots l ON l.workspace_id=r.workspace_id AND"
                    + " l.id=ANY(r.roots) JOIN products p ON p.workspace_id=l.workspace_id AND"
                    + " p.id=l.product_id WHERE r.workspace_id=? AND r.id=? LIMIT 1",
                p.workspaceId(),
                id)
            .get("base_unit"));
    if (list(p).stream().noneMatch(x -> id.toString().equals(x.get("id"))))
      throw DomainError.forbidden();
    var targets =
        db.rows(
            "SELECT rt.id,rt.lot_id,l.code,p.base_unit,rt.site_id,s.name"
                + " site_name,s.organization_id,o.name"
                + " organization_name,rt.transfer_id,rt.quantity,EXISTS(SELECT 1 FROM"
                + " acknowledgments a WHERE a.workspace_id=rt.workspace_id AND"
                + " a.recall_id=rt.recall_id AND a.organization_id=s.organization_id)"
                + " acknowledged,COALESCE((SELECT sum(quantity) FROM balances b WHERE"
                + " b.workspace_id=rt.workspace_id AND b.lot_id=rt.lot_id AND b.site_id=rt.site_id"
                + " AND b.transfer_id IS NOT DISTINCT FROM rt.transfer_id AND b.status IN"
                + " ('RECOVERED','DISPOSED','LOST')),0) accounted,COALESCE((SELECT sum(quantity)"
                + " FROM balances b WHERE b.workspace_id=rt.workspace_id AND b.lot_id=rt.lot_id AND"
                + " b.site_id=rt.site_id AND b.transfer_id IS NOT DISTINCT FROM rt.transfer_id AND"
                + " b.status='UNRESOLVED'),0) unresolved FROM recall_targets rt JOIN sites s ON"
                + " s.workspace_id=rt.workspace_id AND s.id=rt.site_id JOIN organizations o ON"
                + " o.workspace_id=s.workspace_id AND o.id=s.organization_id JOIN lots l ON"
                + " l.workspace_id=rt.workspace_id AND l.id=rt.lot_id JOIN products p ON"
                + " p.workspace_id=l.workspace_id AND p.id=l.product_id WHERE rt.workspace_id=? AND"
                + " rt.recall_id=? AND rt.quantity>0 AND "
                + scope.sitePredicate(p, "s")
                + " ORDER BY o.name,l.code,rt.site_id,rt.transfer_id NULLS FIRST,rt.id",
            p.workspaceId(),
            id);
    var revisions =
        p.network()
            ? db.rows(
                "SELECT * FROM recall_revisions WHERE workspace_id=? AND recall_id=? ORDER BY"
                    + " revision",
                p.workspaceId(),
                id)
            : db.rows(
                "SELECT revision,sequence,'Scope revised; inspect your current target rows.' reason"
                    + " FROM recall_revisions WHERE workspace_id=? AND recall_id=? ORDER BY"
                    + " revision",
                p.workspaceId(),
                id);
    return Map.of(
        "recall",
        recall,
        "targets",
        targets,
        "revisions",
        revisions,
        "acknowledgments",
        db.rows(
            "SELECT a.*,u.name actor_name FROM acknowledgments a JOIN users u ON u.id=a.actor_id"
                + " WHERE a.workspace_id=? AND a.recall_id=? AND "
                + (p.network() ? "true" : "a.organization_id='" + p.organizationId() + "'"),
            p.workspaceId(),
            id),
        "closure",
        closure(p, id),
        "policy",
        "Each current holder must acknowledge; every affected unit must be recovered, disposed or"
            + " recorded as accountable loss. Acknowledgment alone does not account for stock.");
  }

  public Map<String, Object> closure(Member p, UUID recall) {
    UUID w = p.workspaceId();
    long unanswered =
        db.count(
            "SELECT count(DISTINCT s.organization_id) FROM recall_targets rt JOIN sites s ON"
                + " s.workspace_id=rt.workspace_id AND s.id=rt.site_id WHERE rt.workspace_id=? AND"
                + " rt.recall_id=? AND rt.quantity>0 AND NOT EXISTS(SELECT 1 FROM acknowledgments a"
                + " WHERE a.workspace_id=rt.workspace_id AND a.recall_id=rt.recall_id AND"
                + " a.organization_id=s.organization_id)",
            w,
            recall);
    String unresolved =
        db.one(
                "SELECT COALESCE(sum(b.quantity),0) quantity FROM balances b JOIN recall_lots rl ON"
                    + " rl.workspace_id=b.workspace_id AND rl.lot_id=b.lot_id WHERE"
                    + " rl.workspace_id=? AND rl.recall_id=? AND b.status IN"
                    + " ('AVAILABLE','RESERVED','HELD','IN_TRANSIT','UNRESOLVED')",
                w,
                recall)
            .get("quantity")
            .toString();
    long gaps =
        db.count(
            "SELECT count(*) FROM custody_observations o JOIN recall_lots rl ON"
                + " rl.workspace_id=o.workspace_id AND rl.lot_id=o.lot_id WHERE rl.workspace_id=?"
                + " AND rl.recall_id=? AND NOT EXISTS(SELECT 1 FROM observation_resolutions r WHERE"
                + " r.workspace_id=o.workspace_id AND r.observation_id=o.id)",
            w,
            recall);
    return Map.of(
        "canClose",
        unanswered == 0 && new BigDecimal(unresolved).signum() == 0 && gaps == 0,
        "unansweredOrganizations",
        p.network() ? unanswered : -1,
        "unaccountedQuantity",
        p.network()
            ? unresolved
            : "See your target rows; network closure is managed by the recall manager.",
        "openEvidenceGaps",
        p.network() ? gaps : -1);
  }

  public Map<String, Object> acknowledge(Member p, UUID recall, String key, Ack r) {
    return bus.run(
        p,
        "RECALL_ACKNOWLEDGED:" + recall,
        key,
        r,
        null,
        c -> {
          if (db.count(
                  "SELECT count(*) FROM recall_targets rt JOIN sites s ON"
                      + " s.workspace_id=rt.workspace_id AND s.id=rt.site_id WHERE"
                      + " rt.workspace_id=? AND rt.recall_id=? AND s.organization_id=? AND"
                      + " rt.quantity>0 AND ("
                      + scope.sitePredicate(p, "s")
                      + ")",
                  p.workspaceId(),
                  recall,
                  p.organizationId())
              == 0) throw DomainError.forbidden();
          if (db.count(
                  "SELECT count(*) FROM acknowledgments WHERE workspace_id=? AND recall_id=? AND"
                      + " organization_id=?",
                  p.workspaceId(),
                  recall,
                  p.organizationId())
              > 0)
            throw new DomainError(
                409,
                "ALREADY_ACKNOWLEDGED",
                "Your organization already acknowledged this incident. Recovery is a separate"
                    + " quantity action.");
          UUID id = UUID.randomUUID();
          db.update(
              "INSERT INTO acknowledgments VALUES(?,?,?,?,?,?,?)",
              p.workspaceId(),
              id,
              recall,
              p.organizationId(),
              p.userId(),
              c.sequence(),
              r.note());
          bus.notify(
              c,
              p.organizationId(),
              "Recall acknowledgment recorded; physical stock accounting remains separate.");
          return Map.of("id", id.toString());
        });
  }

  public Map<String, Object> close(Member p, UUID recall, String key, Decision r) {
    p.manageRecall();
    return bus.run(
        p,
        "RECALL_CLOSED:" + recall,
        key,
        r,
        null,
        c -> {
          var row =
              db.one(
                  "SELECT status FROM recalls WHERE workspace_id=? AND id=?",
                  p.workspaceId(),
                  recall);
          if (!"ACTIVE".equals(row.get("status")))
            throw new DomainError(409, "RECALL_STATE", "Only an active incident can be closed.");
          var policy = closure(p, recall);
          if (!Boolean.TRUE.equals(policy.get("canClose")))
            throw new DomainError(
                422,
                "CLOSURE_BLOCKED",
                "Responses and exact quantity accounting are required. Resolve remaining stock or"
                    + " record an authorized accountable loss with evidence.",
                policy);
          db.update(
              "UPDATE recalls SET status='CLOSED',closed_sequence=?,version=version+1 WHERE"
                  + " workspace_id=? AND id=?",
              c.sequence(),
              p.workspaceId(),
              recall);
          revision(c, recall, "Closure: " + r.reason());
          notifyTargets(c, recall, "Recall closed after responses and quantity accounting.");
          return Map.of("id", recall.toString(), "status", "CLOSED");
        });
  }

  public Map<String, Object> reopen(Member p, UUID recall, String key, Decision r) {
    p.manageRecall();
    return bus.run(
        p,
        "RECALL_REOPENED:" + recall,
        key,
        r,
        null,
        c -> {
          db.one("SELECT id FROM recalls WHERE workspace_id=? AND id=?", p.workspaceId(), recall);
          db.update(
              "UPDATE recalls SET status='ACTIVE',closed_sequence=NULL,version=version+1 WHERE"
                  + " workspace_id=? AND id=?",
              p.workspaceId(),
              recall);
          hold(c, recall);
          refreshTargets(c, recall);
          revision(c, recall, "Reopened: " + r.reason());
          notifyTargets(c, recall, "Recall reopened: " + r.reason());
          return Map.of("id", recall.toString(), "status", "ACTIVE");
        });
  }
}
