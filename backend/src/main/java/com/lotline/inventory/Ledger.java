package com.lotline.inventory;

import com.lotline.shared.*;
import com.lotline.shared.CommandBus.Command;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class Ledger {
  private final Db db;

  public Ledger(Db db) {
    this.db = db;
  }

  public BigDecimal quantity(UUID workspace, UUID lot, UUID site, String status, UUID transfer) {
    var rows =
        db.rows(
            "SELECT quantity FROM balances WHERE workspace_id=? AND lot_id=? AND site_id=? AND"
                + " status=? AND transfer_id IS NOT DISTINCT FROM ?",
            workspace,
            lot,
            site,
            status,
            transfer);
    return rows.isEmpty()
        ? BigDecimal.ZERO
        : new BigDecimal(rows.getFirst().get("quantity").toString());
  }

  public void delta(
      Command c, UUID lot, UUID site, String status, UUID transfer, BigDecimal amount) {
    if (amount.signum() == 0) return;
    UUID w = c.member().workspaceId();
    db.update("INSERT INTO event_sites VALUES(?,?,?) ON CONFLICT DO NOTHING", w, c.eventId(), site);
    if (quantity(w, lot, site, status, transfer).add(amount).signum() < 0)
      throw DomainError.invalid(
          "INSUFFICIENT_STOCK",
          "Lot "
              + lot
              + " has insufficient "
              + status.toLowerCase(Locale.ROOT)
              + " stock at this site.");
    db.update(
        "INSERT INTO"
            + " inventory_journal(workspace_id,id,event_id,sequence,lot_id,site_id,status,transfer_id,delta)"
            + " VALUES(?,?,?,?,?,?,?,?,?)",
        w,
        UUID.randomUUID(),
        c.eventId(),
        c.sequence(),
        lot,
        site,
        status,
        transfer,
        amount);
    if (amount.signum() < 0)
      db.update(
          "UPDATE balances SET quantity=quantity+? WHERE workspace_id=? AND lot_id=? AND site_id=?"
              + " AND status=? AND transfer_id IS NOT DISTINCT FROM ?",
          amount,
          w,
          lot,
          site,
          status,
          transfer);
    else
      db.update(
          "INSERT INTO balances(workspace_id,lot_id,site_id,status,transfer_id,quantity)"
              + " VALUES(?,?,?,?,?,?) ON CONFLICT(workspace_id,lot_id,site_id,status,transfer_id)"
              + " DO UPDATE SET quantity=balances.quantity+excluded.quantity",
          w,
          lot,
          site,
          status,
          transfer,
          amount);
  }

  public void move(
      Command c,
      UUID lot,
      UUID fromSite,
      String fromStatus,
      UUID fromTransfer,
      UUID toSite,
      String toStatus,
      UUID toTransfer,
      BigDecimal amount) {
    delta(c, lot, fromSite, fromStatus, fromTransfer, amount.negate());
    delta(c, lot, toSite, toStatus, toTransfer, amount);
  }

  public boolean affected(UUID w, UUID lot) {
    return db.count(
            "SELECT count(*) FROM recall_lots rl JOIN recalls r ON r.workspace_id=rl.workspace_id"
                + " AND r.id=rl.recall_id WHERE rl.workspace_id=? AND rl.lot_id=? AND"
                + " r.status='ACTIVE'",
            w,
            lot)
        > 0;
  }

  public void available(UUID w, UUID lot, UUID site) {
    var recalls =
        db.rows(
            "SELECT r.id,r.title FROM recall_lots rl JOIN recalls r ON"
                + " r.workspace_id=rl.workspace_id AND r.id=rl.recall_id WHERE rl.workspace_id=?"
                + " AND rl.lot_id=? AND r.status='ACTIVE' ORDER BY r.id",
            w,
            lot);
    if (!recalls.isEmpty())
      throw new DomainError(
          422,
          "RECALL_HOLD",
          "Lot "
              + lot
              + " is held by incident “"
              + recalls.getFirst().get("title")
              + "”. Only receipt into held stock or accountable recovery/disposition is permitted.",
          Map.of("lotId", lot.toString(), "recalls", recalls));
    if (quantity(w, lot, site, "HELD", null).signum() > 0
        && quantity(w, lot, site, "AVAILABLE", null).signum() == 0)
      throw DomainError.invalid(
          "STOCK_HELD",
          "Lot " + lot + " is held at this site. Record recovery or an authorized disposition.");
  }
}
