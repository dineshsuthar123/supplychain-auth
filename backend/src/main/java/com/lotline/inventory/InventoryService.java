package com.lotline.inventory;

import com.lotline.identity.Member;
import com.lotline.recall.RecallService;
import com.lotline.shared.*;
import com.lotline.workspace.Scope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {
  public record Commission(
      @NotNull UUID productId,
      @NotNull UUID siteId,
      @NotBlank @Size(max = 120) String code,
      @NotBlank String quantity,
      @NotNull LocalDate issuedOn,
      LocalDate expiresOn,
      String occurredAt) {}

  public record Input(@NotNull UUID lotId, @NotBlank String quantity) {}

  public record Output(@NotBlank @Size(max = 120) String code, @NotBlank String quantity) {}

  public record Operation(
      @NotBlank String kind,
      @NotNull UUID siteId,
      @NotEmpty @Size(max = 30) List<@Valid Input> inputs,
      @NotEmpty @Size(max = 30) List<@Valid Output> outputs,
      @NotBlank String declaredLoss,
      String occurredAt) {}

  public record Disposition(
      @NotNull UUID lotId,
      @NotNull UUID siteId,
      @NotBlank String kind,
      @NotBlank String sourceStatus,
      @NotBlank String quantity,
      @NotBlank @Size(max = 1000) String reason,
      @NotBlank @Size(max = 1000) String evidence,
      String occurredAt) {}

  public record Correction(
      @NotNull UUID lotId,
      @NotNull UUID siteId,
      @NotNull UUID correctsEventId,
      @NotBlank String direction,
      @NotBlank String sourceStatus,
      @NotBlank String quantity,
      @NotBlank @Size(max = 1000) String reason,
      @NotBlank @Size(max = 1000) String evidence,
      String occurredAt) {}

  private final Db db;
  private final CommandBus bus;
  private final Scope scope;
  private final Ledger ledger;
  private final RecallService recalls;

  public InventoryService(
      Db db, CommandBus bus, Scope scope, Ledger ledger, RecallService recalls) {
    this.db = db;
    this.bus = bus;
    this.scope = scope;
    this.ledger = ledger;
    this.recalls = recalls;
  }

  public Map<String, Object> commission(Member p, String key, Commission r) {
    if ("PARTNER_OPERATOR".equals(p.role())) throw DomainError.forbidden();
    return bus.run(
        p,
        "LOT_COMMISSIONED",
        key,
        r,
        r.occurredAt(),
        c -> {
          var site = scope.site(p, r.siteId(), true);
          db.one(
              "SELECT id FROM products WHERE workspace_id=? AND id=?",
              p.workspaceId(),
              r.productId());
          if (r.expiresOn() != null && r.expiresOn().isBefore(r.issuedOn()))
            throw DomainError.invalid("LOT_DATES", "Expiry cannot precede issue date.");
          BigDecimal quantity = Quantities.parse(r.quantity());
          UUID lot = UUID.randomUUID();
          db.update(
              "INSERT INTO lots VALUES(?,?,?,?,?,?,?,?)",
              p.workspaceId(),
              lot,
              r.productId(),
              db.uuid(site.get("organization_id")),
              r.code(),
              r.issuedOn(),
              r.expiresOn(),
              c.sequence());
          ledger.delta(c, lot, r.siteId(), "AVAILABLE", null, quantity);
          return Map.of(
              "id", lot.toString(), "code", r.code(), "quantity", Quantities.text(quantity));
        });
  }

  public Map<String, Object> operation(Member p, String key, Operation r) {
    if ("PARTNER_OPERATOR".equals(p.role())) throw DomainError.forbidden();
    return bus.run(
        p,
        "LOT_TRANSFORMED",
        key,
        r,
        r.occurredAt(),
        c -> {
          var site = scope.site(p, r.siteId(), true);
          if (!Set.of("SPLIT", "MERGE", "REPACKAGE").contains(r.kind()))
            throw DomainError.invalid("OPERATION_KIND", "Choose split, merge or repackage.");
          if ("MERGE".equals(r.kind()) && r.inputs().size() < 2)
            throw DomainError.invalid(
                "MERGE_INPUTS", "Merge requires at least two distinct input lots.");
          Set<UUID> ids = new HashSet<>();
          UUID product = null;
          BigDecimal total = BigDecimal.ZERO,
              outputTotal = BigDecimal.ZERO,
              loss = Quantities.parse(r.declaredLoss(), true);
          LocalDate earliestExpiry = null;
          for (Input in : r.inputs()) {
            if (!ids.add(in.lotId()))
              throw DomainError.invalid("DUPLICATE_INPUT", "Use each input lot once.");
            var lot =
                db.one(
                    "SELECT * FROM lots WHERE workspace_id=? AND id=?",
                    p.workspaceId(),
                    in.lotId());
            UUID prod = db.uuid(lot.get("product_id"));
            if (product != null && !product.equals(prod))
              throw DomainError.invalid(
                  "INCOMPATIBLE_PRODUCT",
                  "Only the same product and base unit can be transformed. No physical yield is"
                      + " inferred.");
            product = prod;
            if (lot.get("expires_on") != null) {
              LocalDate exp = LocalDate.parse(lot.get("expires_on").toString());
              if (earliestExpiry == null || exp.isBefore(earliestExpiry)) earliestExpiry = exp;
            }
            ledger.available(p.workspaceId(), in.lotId(), r.siteId());
            BigDecimal q = Quantities.parse(in.quantity());
            if (ledger
                    .quantity(p.workspaceId(), in.lotId(), r.siteId(), "AVAILABLE", null)
                    .compareTo(q)
                < 0)
              throw DomainError.invalid(
                  "INSUFFICIENT_STOCK",
                  "Input lot " + lot.get("code") + " has insufficient available stock.");
            total = total.add(q);
          }
          for (Output out : r.outputs())
            outputTotal = outputTotal.add(Quantities.parse(out.quantity()));
          if (total.compareTo(outputTotal.add(loss)) != 0)
            throw DomainError.invalid(
                "CONSERVATION_FAILED",
                "Outputs ("
                    + Quantities.text(outputTotal)
                    + ") + declared loss ("
                    + Quantities.text(loss)
                    + ") must equal consumed inputs ("
                    + Quantities.text(total)
                    + ").");
          UUID operation = UUID.randomUUID();
          db.update(
              "INSERT INTO operations VALUES(?,?,?,?,?,?)",
              p.workspaceId(),
              operation,
              r.kind(),
              r.siteId(),
              loss,
              c.sequence());
          for (Input in : r.inputs()) {
            BigDecimal q = Quantities.parse(in.quantity());
            ledger.move(
                c, in.lotId(), r.siteId(), "AVAILABLE", null, r.siteId(), "CONSUMED", null, q);
            db.update(
                "INSERT INTO operation_inputs VALUES(?,?,?,?)",
                p.workspaceId(),
                operation,
                in.lotId(),
                q);
          }
          List<Map<String, Object>> outputs = new ArrayList<>();
          for (Output out : r.outputs()) {
            UUID id = UUID.randomUUID();
            BigDecimal q = Quantities.parse(out.quantity());
            db.update(
                "INSERT INTO lots VALUES(?,?,?,?,?,?,?,?)",
                p.workspaceId(),
                id,
                product,
                db.uuid(site.get("organization_id")),
                out.code(),
                LocalDate.now(),
                earliestExpiry,
                c.sequence());
            db.update(
                "INSERT INTO operation_outputs VALUES(?,?,?,?)", p.workspaceId(), operation, id, q);
            ledger.delta(c, id, r.siteId(), "AVAILABLE", null, q);
            for (Input in : r.inputs())
              db.update(
                  "INSERT INTO lineage VALUES(?,?,?,?,?)",
                  p.workspaceId(),
                  in.lotId(),
                  id,
                  operation,
                  c.sequence());
            outputs.add(
                Map.of("id", id.toString(), "code", out.code(), "quantity", Quantities.text(q)));
          }
          return Map.of(
              "id",
              operation.toString(),
              "outputs",
              outputs,
              "declaredLoss",
              Quantities.text(loss));
        });
  }

  public Map<String, Object> disposition(Member p, String key, Disposition r) {
    return bus.run(
        p,
        "STOCK_DISPOSITION",
        key,
        r,
        r.occurredAt(),
        c -> {
          var site = scope.site(p, r.siteId(), true);
          scope.lot(p, r.lotId());
          String target;
          switch (r.kind()) {
            case "HOLD" -> {
              if (!"AVAILABLE".equals(r.sourceStatus()))
                throw DomainError.invalid(
                    "DISPOSITION_SOURCE", "Manual holds consume available stock.");
              target = "HELD";
            }
            case "RECOVER" -> {
              if (!"HELD".equals(r.sourceStatus()))
                throw DomainError.invalid("DISPOSITION_SOURCE", "Recovery requires held stock.");
              target = "RECOVERED";
            }
            case "DISPOSE" -> {
              if (!Set.of("HELD", "RECOVERED").contains(r.sourceStatus()))
                throw DomainError.invalid(
                    "DISPOSITION_SOURCE", "Disposal requires held or recovered stock.");
              target = "DISPOSED";
            }
            case "LOSS" -> {
              p.manageRecall();
              if (!"HELD".equals(r.sourceStatus()))
                throw DomainError.invalid(
                    "DISPOSITION_SOURCE", "Accountable loss consumes held stock.");
              target = "LOST";
            }
            default ->
                throw DomainError.invalid(
                    "DISPOSITION_KIND", "Choose hold, recover, dispose or accountable loss.");
          }
          BigDecimal q = Quantities.parse(r.quantity());
          ledger.move(
              c, r.lotId(), r.siteId(), r.sourceStatus(), null, r.siteId(), target, null, q);
          UUID id = UUID.randomUUID();
          db.update(
              "INSERT INTO dispositions VALUES(?,?,?,?,?,?,?,?,?,?)",
              p.workspaceId(),
              id,
              r.lotId(),
              r.siteId(),
              r.kind(),
              q,
              r.reason(),
              r.evidence(),
              p.userId(),
              c.sequence());
          bus.notify(
              c,
              db.uuid(site.get("organization_id")),
              "Stock " + r.kind().toLowerCase(Locale.ROOT) + ": " + Quantities.text(q));
          return Map.of("id", id.toString(), "status", target);
        });
  }

  public Map<String, Object> correction(Member p, String key, Correction r) {
    p.manageRecall();
    return bus.run(
        p,
        "STOCK_CORRECTED",
        key,
        r,
        r.occurredAt(),
        c -> {
          scope.site(p, r.siteId(), true);
          scope.lot(p, r.lotId());
          if (db.count(
                  "SELECT count(*) FROM inventory_journal WHERE workspace_id=? AND event_id=? AND"
                      + " lot_id=?",
                  p.workspaceId(),
                  r.correctsEventId(),
                  r.lotId())
              == 0)
            throw DomainError.invalid(
                "CORRECTION_LINK",
                "Link a prior accepted inventory event for this lot. Corrections append new facts"
                    + " at the current sequence.");
          if (!Set.of("ADD", "REMOVE").contains(r.direction())
              || !Set.of("AVAILABLE", "HELD").contains(r.sourceStatus()))
            throw DomainError.invalid(
                "CORRECTION_KIND",
                "Use add/remove and available/held stock with a documented correction reference.");
          BigDecimal q = Quantities.parse(r.quantity());
          String status =
              "ADD".equals(r.direction()) && ledger.affected(p.workspaceId(), r.lotId())
                  ? "HELD"
                  : r.sourceStatus();
          ledger.delta(
              c, r.lotId(), r.siteId(), status, null, "ADD".equals(r.direction()) ? q : q.negate());
          if ("ADD".equals(r.direction()))
            recalls.newHoldings(
                c, r.lotId(), "Accepted correction adds " + r.quantity() + " " + r.reason());
          return Map.of("eventId", c.eventId().toString(), "status", status);
        });
  }
}
