package com.lotline.custody;

import com.lotline.identity.Member;
import com.lotline.inventory.Ledger;
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
public class CustodyService {
  public record Line(@NotNull UUID lotId, @NotBlank String quantity) {}

  public record Draft(
      @NotNull UUID sourceSiteId,
      @NotNull UUID destinationSiteId,
      @NotBlank @Size(max = 160) String reference,
      @NotEmpty @Size(max = 30) List<@Valid Line> lines,
      boolean reserve,
      String occurredAt) {}

  public record Dispatch(long version, String occurredAt) {}

  public record Received(
      @NotNull UUID lotId,
      @NotBlank String quantity,
      @NotBlank String condition,
      @Size(max = 1000) String reason) {}

  public record Receipt(
      long version, @NotEmpty @Size(max = 30) List<@Valid Received> lines, String occurredAt) {}

  public record Resolve(
      @NotNull UUID discrepancyId,
      @NotBlank String disposition,
      @NotBlank String quantity,
      @NotBlank @Size(max = 1000) String reason,
      @NotBlank @Size(max = 1000) String evidence,
      String occurredAt) {}

  public record Cancel(@NotBlank @Size(max = 1000) String reason, long version) {}

  private final Db db;
  private final CommandBus bus;
  private final Scope scope;
  private final Ledger ledger;
  private final RecallService recalls;

  public CustodyService(Db db, CommandBus bus, Scope scope, Ledger ledger, RecallService recalls) {
    this.db = db;
    this.bus = bus;
    this.scope = scope;
    this.ledger = ledger;
    this.recalls = recalls;
  }

  private Map<String, Object> transfer(Member p, UUID id) {
    var t = db.one("SELECT * FROM transfers WHERE workspace_id=? AND id=?", p.workspaceId(), id);
    var source =
        db.one(
            "SELECT * FROM sites WHERE workspace_id=? AND id=?",
            p.workspaceId(),
            db.uuid(t.get("source_site_id")));
    var dest =
        db.one(
            "SELECT * FROM sites WHERE workspace_id=? AND id=?",
            p.workspaceId(),
            db.uuid(t.get("destination_site_id")));
    if (!scope.seesSite(p, source.get("id"), source.get("organization_id"))
        && !scope.seesSite(p, dest.get("id"), dest.get("organization_id")))
      throw DomainError.forbidden();
    return t;
  }

  private void version(Map<String, Object> row, long expected) {
    if (((Number) row.get("version")).longValue() != expected)
      throw new DomainError(
          409,
          "STALE_ENTITY",
          "This shipment changed. Refresh its facts before submitting.",
          Map.of("version", row.get("version")));
  }

  public List<Map<String, Object>> list(Member p) {
    var all =
        db.rows(
            "SELECT t.*,ss.name source_site,ds.name destination_site,so.name"
                + " source_organization,dorg.name destination_organization FROM transfers t JOIN"
                + " sites ss ON ss.workspace_id=t.workspace_id AND ss.id=t.source_site_id JOIN"
                + " sites ds ON ds.workspace_id=t.workspace_id AND ds.id=t.destination_site_id JOIN"
                + " organizations so ON so.workspace_id=t.workspace_id AND"
                + " so.id=t.source_organization_id JOIN organizations dorg ON"
                + " dorg.workspace_id=t.workspace_id AND dorg.id=t.destination_organization_id"
                + " WHERE t.workspace_id=? ORDER BY t.created_sequence DESC LIMIT 500",
            p.workspaceId());
    return all.stream()
        .filter(
            t ->
                scope.seesSite(p, t.get("source_site_id"), t.get("source_organization_id"))
                    || scope.seesSite(
                        p, t.get("destination_site_id"), t.get("destination_organization_id")))
        .toList();
  }

  public Map<String, Object> detail(Member p, UUID id) {
    var t = transfer(p, id);
    return Map.of(
        "transfer",
        t,
        "lines",
        db.rows(
            "SELECT tl.lot_id,tl.quantity,tl.reserved,l.code,p.base_unit,EXISTS(SELECT 1 FROM"
                + " recall_lots rl JOIN recalls r ON r.workspace_id=rl.workspace_id AND"
                + " r.id=rl.recall_id WHERE rl.workspace_id=tl.workspace_id AND rl.lot_id=tl.lot_id"
                + " AND r.status='ACTIVE') held FROM transfer_lines tl JOIN lots l ON"
                + " l.workspace_id=tl.workspace_id AND l.id=tl.lot_id JOIN products p ON"
                + " p.workspace_id=l.workspace_id AND p.id=l.product_id WHERE tl.workspace_id=? AND"
                + " tl.transfer_id=? ORDER BY l.code",
            p.workspaceId(),
            id),
        "receipts",
        db.rows(
            "SELECT r.*,u.name actor_name,e.occurred_at,e.recorded_at FROM receipts r JOIN users u"
                + " ON u.id=r.actor_id JOIN events e ON e.workspace_id=r.workspace_id AND"
                + " e.sequence=r.sequence WHERE r.workspace_id=? AND r.transfer_id=?",
            p.workspaceId(),
            id),
        "discrepancies",
        db.rows(
            "SELECT * FROM discrepancies WHERE workspace_id=? AND transfer_id=? ORDER BY lot_id",
            p.workspaceId(),
            id),
        "resolutions",
        db.rows(
            "SELECT r.* FROM discrepancy_resolutions r JOIN discrepancies d ON"
                + " d.workspace_id=r.workspace_id AND d.id=r.discrepancy_id WHERE d.workspace_id=?"
                + " AND d.transfer_id=? ORDER BY r.sequence",
            p.workspaceId(),
            id));
  }

  public Map<String, Object> draft(Member p, String key, Draft r) {
    return bus.run(
        p,
        "TRANSFER_DRAFTED",
        key,
        r,
        r.occurredAt(),
        c -> {
          var source = scope.site(p, r.sourceSiteId(), true);
          var dest =
              db.one(
                  "SELECT * FROM sites WHERE workspace_id=? AND id=?",
                  p.workspaceId(),
                  r.destinationSiteId());
          bus.site(c, r.sourceSiteId());
          bus.site(c, r.destinationSiteId());
          if (r.sourceSiteId().equals(r.destinationSiteId()))
            throw DomainError.invalid("SAME_SITE", "Choose a different destination site.");
          UUID id = UUID.randomUUID();
          db.update(
              "INSERT INTO"
                  + " transfers(workspace_id,id,source_site_id,destination_site_id,source_organization_id,destination_organization_id,status,reference,created_sequence)"
                  + " VALUES(?,?,?,?,?,?,'DRAFT',?,?)",
              p.workspaceId(),
              id,
              r.sourceSiteId(),
              r.destinationSiteId(),
              db.uuid(source.get("organization_id")),
              db.uuid(dest.get("organization_id")),
              r.reference(),
              c.sequence());
          Set<UUID> lots = new HashSet<>();
          for (Line line : r.lines()) {
            scope.lot(p, line.lotId());
            if (!lots.add(line.lotId()))
              throw DomainError.invalid("DUPLICATE_LINE", "Use each lot once per shipment.");
            BigDecimal quantity = Quantities.parse(line.quantity());
            ledger.available(p.workspaceId(), line.lotId(), r.sourceSiteId());
            if (ledger
                    .quantity(p.workspaceId(), line.lotId(), r.sourceSiteId(), "AVAILABLE", null)
                    .compareTo(quantity)
                < 0)
              throw DomainError.invalid(
                  "INSUFFICIENT_STOCK",
                  "Lot " + line.lotId() + " does not have the requested available stock.");
            db.update(
                "INSERT INTO transfer_lines VALUES(?,?,?,?,?)",
                p.workspaceId(),
                id,
                line.lotId(),
                quantity,
                r.reserve());
            if (r.reserve())
              ledger.move(
                  c,
                  line.lotId(),
                  r.sourceSiteId(),
                  "AVAILABLE",
                  null,
                  r.sourceSiteId(),
                  "RESERVED",
                  id,
                  quantity);
          }
          return Map.of("id", id.toString(), "status", "DRAFT", "version", 0);
        });
  }

  public Map<String, Object> dispatch(Member p, UUID id, String key, Dispatch r) {
    return bus.run(
        p,
        "TRANSFER_DISPATCHED:" + id,
        key,
        r,
        r.occurredAt(),
        c -> {
          var t = transfer(p, id);
          bus.site(c, db.uuid(t.get("source_site_id")));
          bus.site(c, db.uuid(t.get("destination_site_id")));
          scope.site(p, db.uuid(t.get("source_site_id")), true);
          version(t, r.version());
          if (!"DRAFT".equals(t.get("status")))
            throw new DomainError(409, "TRANSFER_STATE", "Only a draft can be dispatched.");
          for (var line :
              db.rows(
                  "SELECT tl.*,l.expires_on,l.code FROM transfer_lines tl JOIN lots l ON"
                      + " l.workspace_id=tl.workspace_id AND l.id=tl.lot_id WHERE tl.workspace_id=?"
                      + " AND tl.transfer_id=? ORDER BY lot_id",
                  p.workspaceId(),
                  id)) {
            UUID lot = db.uuid(line.get("lot_id")),
                source = db.uuid(t.get("source_site_id")),
                dest = db.uuid(t.get("destination_site_id"));
            ledger.available(p.workspaceId(), lot, source);
            if (line.get("expires_on") != null
                && LocalDate.parse(line.get("expires_on").toString())
                    .isBefore(LocalDate.now(java.time.Clock.systemUTC())))
              throw DomainError.invalid(
                  "EXPIRED_LOT",
                  "Lot "
                      + line.get("code")
                      + " has expired. Dispatch is blocked by this workspace's v1 policy.");
            boolean reserved = Boolean.TRUE.equals(line.get("reserved"));
            ledger.move(
                c,
                lot,
                source,
                reserved ? "RESERVED" : "AVAILABLE",
                reserved ? id : null,
                dest,
                "IN_TRANSIT",
                id,
                new BigDecimal(line.get("quantity").toString()));
          }
          db.update(
              "UPDATE transfers SET status='DISPATCHED',version=version+1 WHERE workspace_id=? AND"
                  + " id=?",
              p.workspaceId(),
              id);
          bus.notify(
              c,
              db.uuid(t.get("destination_organization_id")),
              "Shipment dispatched: " + t.get("reference") + ". Confirm actual quantities.");
          return Map.of("id", id.toString(), "status", "DISPATCHED", "version", r.version() + 1);
        });
  }

  public Map<String, Object> receive(Member p, UUID id, String key, Receipt r) {
    return bus.run(
        p,
        "TRANSFER_RECEIVED:" + id,
        key,
        r,
        r.occurredAt(),
        c -> {
          var t = transfer(p, id);
          bus.site(c, db.uuid(t.get("source_site_id")));
          bus.site(c, db.uuid(t.get("destination_site_id")));
          if (!p.organizationId().toString().equals(t.get("destination_organization_id")))
            throw new DomainError(
                403,
                "RECEIVER_REQUIRED",
                "Only an operator of the destination organization may assert a receipt.");
          UUID dest = db.uuid(t.get("destination_site_id"));
          scope.site(p, dest, true);
          version(t, r.version());
          if (!"DISPATCHED".equals(t.get("status")))
            throw new DomainError(
                409,
                "TRANSFER_STATE",
                "This shipment is not awaiting a receipt. A second receipt cannot create more"
                    + " stock.");
          var sent =
              db.rows(
                  "SELECT * FROM transfer_lines WHERE workspace_id=? AND transfer_id=? ORDER BY"
                      + " lot_id",
                  p.workspaceId(),
                  id);
          Map<UUID, Received> received = new HashMap<>();
          for (Received line : r.lines())
            if (received.put(line.lotId(), line) != null)
              throw DomainError.invalid("DUPLICATE_LINE", "Report each lot once.");
          if (sent.size() != received.size())
            throw DomainError.invalid(
                "RECEIPT_LINES",
                "Report every dispatched line, including zero received quantities.");
          boolean disputed = false;
          for (var line : sent) {
            UUID lot = db.uuid(line.get("lot_id"));
            Received got = received.get(lot);
            if (got == null)
              throw DomainError.invalid(
                  "RECEIPT_LINES", "Receipt lines must match dispatched lots.");
            BigDecimal actual = Quantities.parse(got.quantity(), true),
                claimed = new BigDecimal(line.get("quantity").toString());
            if (actual.compareTo(claimed) > 0)
              throw DomainError.invalid(
                  "OVER_RECEIPT",
                  "Actual receipt exceeds the dispatched quantity. Over-receipt requires an"
                      + " exception process outside v1.");
            if (!Set.of("GOOD", "DAMAGED", "HELD").contains(got.condition()))
              throw DomainError.invalid("RECEIPT_CONDITION", "Choose good, damaged or held.");
            BigDecimal missing = claimed.subtract(actual);
            if ((missing.signum() > 0 || !"GOOD".equals(got.condition()))
                && (got.reason() == null || got.reason().isBlank()))
              throw DomainError.invalid(
                  "EXCEPTION_REASON", "Explain the quantity discrepancy or reported condition.");
            String state =
                ledger.affected(p.workspaceId(), lot) || !"GOOD".equals(got.condition())
                    ? "HELD"
                    : "AVAILABLE";
            ledger.move(c, lot, dest, "IN_TRANSIT", id, dest, state, null, actual);
            if (missing.signum() > 0) {
              ledger.move(c, lot, dest, "IN_TRANSIT", id, dest, "UNRESOLVED", id, missing);
              db.update(
                  "INSERT INTO discrepancies VALUES(?,?,?,?,?,?,?)",
                  p.workspaceId(),
                  UUID.randomUUID(),
                  id,
                  lot,
                  claimed,
                  actual,
                  missing);
              disputed = true;
            }
            db.update(
                "INSERT INTO receipts VALUES(?,?,?,?,?,?,?,?,?)",
                p.workspaceId(),
                UUID.randomUUID(),
                id,
                lot,
                actual,
                got.condition(),
                got.reason() == null ? "" : got.reason(),
                p.userId(),
                c.sequence());
            recalls.newHoldings(
                c,
                lot,
                "Receiver accepted "
                    + Quantities.text(actual)
                    + " units; transfer-bound quantities reconciled.");
          }
          String status = disputed ? "DISPUTED" : "RECEIVED";
          db.update(
              "UPDATE transfers SET status=?,version=version+1 WHERE workspace_id=? AND id=?",
              status,
              p.workspaceId(),
              id);
          bus.notify(
              c,
              db.uuid(t.get("source_organization_id")),
              "Receiver claim recorded for "
                  + t.get("reference")
                  + ": "
                  + status.toLowerCase(Locale.ROOT));
          return Map.of("id", id.toString(), "status", status, "version", r.version() + 1);
        });
  }

  public Map<String, Object> resolve(Member p, UUID transfer, String key, Resolve r) {
    return bus.run(
        p,
        "DISCREPANCY_RESOLVED:" + transfer,
        key,
        r,
        r.occurredAt(),
        c -> {
          var t = transfer(p, transfer);
          bus.site(c, db.uuid(t.get("source_site_id")));
          bus.site(c, db.uuid(t.get("destination_site_id")));
          var d =
              db.one(
                  "SELECT * FROM discrepancies WHERE workspace_id=? AND id=? AND transfer_id=?",
                  p.workspaceId(),
                  r.discrepancyId(),
                  transfer);
          UUID lot = db.uuid(d.get("lot_id")),
              dest = db.uuid(t.get("destination_site_id")),
              source = db.uuid(t.get("source_site_id"));
          BigDecimal n = Quantities.parse(r.quantity());
          if (new BigDecimal(d.get("unresolved_quantity").toString()).compareTo(n) < 0)
            throw DomainError.invalid(
                "RESOLUTION_QUANTITY",
                "This quantity has already been resolved or exceeds the unresolved remainder.");
          String target;
          UUID site;
          switch (r.disposition()) {
            case "LATER_RECEIVED" -> {
              if (!p.organizationId().toString().equals(t.get("destination_organization_id")))
                throw DomainError.forbidden();
              scope.site(p, dest, true);
              site = dest;
              target = ledger.affected(p.workspaceId(), lot) ? "HELD" : "AVAILABLE";
            }
            case "RETURNED" -> {
              if (!p.organizationId().toString().equals(t.get("source_organization_id")))
                throw DomainError.forbidden();
              scope.site(p, source, true);
              site = source;
              target = ledger.affected(p.workspaceId(), lot) ? "HELD" : "AVAILABLE";
            }
            case "ACCOUNTABLE_LOSS" -> {
              p.manageRecall();
              site = dest;
              target = "LOST";
            }
            default ->
                throw DomainError.invalid(
                    "RESOLUTION_DISPOSITION",
                    "Choose later received, returned or an authorized accountable loss.");
          }
          ledger.move(
              c,
              lot,
              dest,
              "UNRESOLVED",
              transfer,
              site,
              target,
              "LOST".equals(target) ? transfer : null,
              n);
          db.update(
              "INSERT INTO discrepancy_resolutions VALUES(?,?,?,?,?,?,?,?,?)",
              p.workspaceId(),
              UUID.randomUUID(),
              r.discrepancyId(),
              r.disposition(),
              n,
              r.reason(),
              r.evidence(),
              p.userId(),
              c.sequence());
          db.update(
              "UPDATE discrepancies SET unresolved_quantity=unresolved_quantity-? WHERE"
                  + " workspace_id=? AND id=?",
              n,
              p.workspaceId(),
              r.discrepancyId());
          if (db.count(
                  "SELECT count(*) FROM discrepancies WHERE workspace_id=? AND transfer_id=? AND"
                      + " unresolved_quantity>0",
                  p.workspaceId(),
                  transfer)
              == 0)
            db.update(
                "UPDATE transfers SET status='RECONCILED',version=version+1 WHERE workspace_id=?"
                    + " AND id=?",
                p.workspaceId(),
                transfer);
          if (!"LOST".equals(target))
            recalls.newHoldings(
                c,
                lot,
                "Late accepted reconciliation: " + r.disposition() + " " + r.quantity() + ".");
          bus.notify(
              c,
              db.uuid(t.get("destination_organization_id")),
              "Discrepancy resolution: "
                  + r.disposition()
                  + " "
                  + r.quantity()
                  + ". "
                  + r.reason());
          return Map.of("id", r.discrepancyId().toString(), "disposition", r.disposition());
        });
  }

  public Map<String, Object> cancel(Member p, UUID id, String key, Cancel r) {
    return bus.run(
        p,
        "TRANSFER_CANCELLED:" + id,
        key,
        r,
        null,
        c -> {
          var t = transfer(p, id);
          bus.site(c, db.uuid(t.get("source_site_id")));
          bus.site(c, db.uuid(t.get("destination_site_id")));
          UUID source = db.uuid(t.get("source_site_id")),
              dest = db.uuid(t.get("destination_site_id"));
          scope.site(p, source, true);
          version(t, r.version());
          if (!Set.of("DRAFT", "DISPATCHED").contains(t.get("status")))
            throw new DomainError(
                409,
                "TRANSFER_STATE",
                "Only a draft or dispatched shipment can enter cancellation reconciliation.");
          for (var l :
              db.rows(
                  "SELECT * FROM transfer_lines WHERE workspace_id=? AND transfer_id=? ORDER BY"
                      + " lot_id",
                  p.workspaceId(),
                  id)) {
            UUID lot = db.uuid(l.get("lot_id"));
            BigDecimal n = new BigDecimal(l.get("quantity").toString());
            if ("DRAFT".equals(t.get("status"))) {
              BigDecimal reserved = ledger.quantity(p.workspaceId(), lot, source, "RESERVED", id);
              if (reserved.signum() > 0)
                ledger.move(
                    c,
                    lot,
                    source,
                    "RESERVED",
                    id,
                    source,
                    ledger.affected(p.workspaceId(), lot) ? "HELD" : "AVAILABLE",
                    null,
                    reserved);
            } else {
              ledger.move(c, lot, dest, "IN_TRANSIT", id, dest, "UNRESOLVED", id, n);
              db.update(
                  "INSERT INTO discrepancies VALUES(?,?,?,?,?,?,?)",
                  p.workspaceId(),
                  UUID.randomUUID(),
                  id,
                  lot,
                  n,
                  BigDecimal.ZERO,
                  n);
              recalls.newHoldings(
                  c, lot, "Dispatched cancellation requires accountable reconciliation.");
            }
          }
          String status = "DRAFT".equals(t.get("status")) ? "CANCELLED" : "DISPUTED";
          db.update(
              "UPDATE transfers SET status=?,version=version+1 WHERE workspace_id=? AND id=?",
              status,
              p.workspaceId(),
              id);
          bus.notify(
              c,
              db.uuid(t.get("destination_organization_id")),
              "Cancellation recorded: "
                  + r.reason()
                  + ". Dispatched stock remains unresolved until reconciled.");
          return Map.of("id", id.toString(), "status", status);
        });
  }
}
