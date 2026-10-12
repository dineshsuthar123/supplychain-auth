package com.lotline.lineage;

import com.lotline.identity.Member;
import com.lotline.shared.*;
import com.lotline.workspace.Scope;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class TraceService {
  private final Db db;
  private final Scope scope;

  public TraceService(Db db, Scope scope) {
    this.db = db;
    this.scope = scope;
  }

  public long sequence(Member p, Long requested) {
    long current = db.count("SELECT sequence FROM workspaces WHERE id=?", p.workspaceId());
    if (requested != null && (requested < 0 || requested > current))
      throw DomainError.invalid(
          "SNAPSHOT_SEQUENCE", "Choose an accepted sequence from 0 to " + current + ".");
    return requested == null ? current : requested;
  }

  public List<Map<String, Object>> holdings(Member p, long seq) {
    return db.rows(
        "SELECT j.lot_id,l.code,p.name product_name,p.base_unit,j.site_id,s.name"
            + " site_name,s.organization_id,o.name"
            + " organization_name,j.status,j.transfer_id,sum(j.delta) quantity FROM"
            + " inventory_journal j JOIN lots l ON l.workspace_id=j.workspace_id AND l.id=j.lot_id"
            + " JOIN products p ON p.workspace_id=l.workspace_id AND p.id=l.product_id JOIN sites s"
            + " ON s.workspace_id=j.workspace_id AND s.id=j.site_id JOIN organizations o ON"
            + " o.workspace_id=s.workspace_id AND o.id=s.organization_id WHERE j.workspace_id=? AND"
            + " j.sequence<=? AND "
            + scope.journalPredicate(p, "j", "s")
            + " GROUP BY"
            + " j.lot_id,l.code,p.name,p.base_unit,j.site_id,s.name,s.organization_id,o.name,j.status,j.transfer_id"
            + " HAVING sum(j.delta)>0 ORDER BY l.code,s.name,j.status LIMIT 5001",
        p.workspaceId(),
        seq);
  }

  public List<Map<String, Object>> edges(Member p, long seq) {
    return db.rows(
        "SELECT input_lot_id,output_lot_id,operation_id,sequence FROM lineage WHERE workspace_id=?"
            + " AND sequence<=? ORDER BY sequence,input_lot_id,output_lot_id LIMIT 10001",
        p.workspaceId(),
        seq);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Map<String, Object> graph(Member p, Long requested) {
    long seq = sequence(p, requested);
    var holdings = holdings(p, seq);
    var edges = edges(p, seq);
    boolean complete = holdings.size() <= 5000 && edges.size() <= 10000;
    Set<String> visible = new HashSet<>();
    if (!p.network()) {
      holdings.forEach(h -> visible.add(h.get("lot_id").toString()));
      db.rows(
              "SELECT DISTINCT j.lot_id FROM inventory_journal j JOIN sites s ON"
                  + " s.workspace_id=j.workspace_id AND s.id=j.site_id WHERE j.workspace_id=? AND"
                  + " j.sequence<=? AND "
                  + scope.sitePredicate(p, "s"),
              p.workspaceId(),
              seq)
          .forEach(x -> visible.add(x.get("lot_id").toString()));
      db.rows(
              "SELECT tl.lot_id FROM transfer_lines tl JOIN transfers t ON"
                  + " t.workspace_id=tl.workspace_id AND t.id=tl.transfer_id JOIN sites ss ON"
                  + " ss.workspace_id=t.workspace_id AND ss.id=t.source_site_id JOIN sites ds ON"
                  + " ds.workspace_id=t.workspace_id AND ds.id=t.destination_site_id WHERE"
                  + " t.workspace_id=? AND t.created_sequence<=? AND (("
                  + scope.sitePredicate(p, "ss")
                  + ") OR ("
                  + scope.sitePredicate(p, "ds")
                  + "))",
              p.workspaceId(),
              seq)
          .forEach(x -> visible.add(x.get("lot_id").toString()));
      boolean changed = true;
      int rounds = 0;
      while (changed && rounds++ < 500) {
        changed = false;
        for (var e : edges)
          if (visible.contains(e.get("output_lot_id").toString()))
            changed |= visible.add(e.get("input_lot_id").toString());
      }
      if (changed) complete = false;
    }
    var all =
        db.rows(
            "SELECT l.id,l.code,l.product_id,p.name product_name,p.base_unit,l.issuer_id,o.name"
                + " issuer_name,l.issued_on,l.expires_on,l.created_sequence FROM lots l JOIN"
                + " products p ON p.workspace_id=l.workspace_id AND p.id=l.product_id JOIN"
                + " organizations o ON o.workspace_id=l.workspace_id AND o.id=l.issuer_id WHERE"
                + " l.workspace_id=? AND l.created_sequence<=? ORDER BY l.created_sequence,l.code"
                + " LIMIT 2001",
            p.workspaceId(),
            seq);
    if (all.size() > 2000) complete = false;
    var nodes =
        all.stream()
            .limit(2000)
            .filter(l -> p.network() || visible.contains(l.get("id").toString()))
            .toList();
    Set<String> ids = new HashSet<>();
    nodes.forEach(l -> ids.add(l.get("id").toString()));
    var links =
        edges.stream()
            .filter(
                e ->
                    ids.contains(e.get("input_lot_id").toString())
                        && ids.contains(e.get("output_lot_id").toString()))
            .toList();
    return Map.of(
        "sequence",
        seq,
        "historical",
        requested != null,
        "complete",
        complete,
        "scope",
        p.network() ? "network" : "organization and granted sites; relevant ancestors only",
        "lots",
        nodes,
        "lineage",
        links,
        "holdings",
        holdings.stream().limit(5000).toList(),
        "events",
        events(p, seq, Math.max(0, seq - 100), 100),
        "transfers",
        transfers(p, seq));
  }

  public List<Map<String, Object>> events(Member p, long seq, long after, int limit) {
    int bounded = Math.min(Math.max(limit, 1), 200);
    String predicate = scope.eventPredicate(p, "e");
    return db.rows(
        "SELECT e.id,e.sequence,e.type,e.schema_version,e.actor_id,u.name"
            + " actor_name,e.organization_id,o.name"
            + " organization_name,e.command_id,e.payload,e.recorded_at,e.occurred_at FROM events e"
            + " JOIN users u ON u.id=e.actor_id JOIN organizations o ON"
            + " o.workspace_id=e.workspace_id AND o.id=e.organization_id WHERE e.workspace_id=? AND"
            + " e.sequence>? AND e.sequence<=? AND "
            + predicate
            + " ORDER BY e.sequence LIMIT ?",
        p.workspaceId(),
        after,
        seq,
        bounded);
  }

  public List<Map<String, Object>> transfers(Member p, long seq) {
    String predicate =
        p.network()
            ? "true"
            : "((" + scope.sitePredicate(p, "ss") + ") OR (" + scope.sitePredicate(p, "ds") + "))";
    return db.rows(
        "SELECT t.id,t.reference,t.source_site_id,t.destination_site_id,ss.name source_site,ds.name"
            + " destination_site,t.source_organization_id,t.destination_organization_id,t.version,CASE"
            + " WHEN EXISTS(SELECT 1 FROM inventory_journal j WHERE j.workspace_id=t.workspace_id"
            + " AND j.transfer_id=t.id AND j.sequence<=? AND j.status='UNRESOLVED' GROUP BY"
            + " j.transfer_id HAVING sum(j.delta)>0) THEN 'DISPUTED' WHEN EXISTS(SELECT 1 FROM"
            + " events e WHERE e.workspace_id=t.workspace_id AND"
            + " e.type='DISCREPANCY_RESOLVED:'||t.id::text AND e.sequence<=?) THEN 'RECONCILED'"
            + " ELSE COALESCE((SELECT c.response->>'status' FROM events e JOIN idempotency_records"
            + " c ON c.workspace_id=e.workspace_id AND c.command_id=e.command_id WHERE"
            + " e.workspace_id=t.workspace_id AND e.sequence<=? AND c.response->>'id'=t.id::text"
            + " AND c.scope LIKE 'TRANSFER%' AND jsonb_exists(c.response,'status') ORDER BY"
            + " e.sequence DESC LIMIT 1),'DRAFT') END status FROM transfers t JOIN sites ss ON"
            + " ss.workspace_id=t.workspace_id AND ss.id=t.source_site_id JOIN sites ds ON"
            + " ds.workspace_id=t.workspace_id AND ds.id=t.destination_site_id WHERE"
            + " t.workspace_id=? AND t.created_sequence<=? AND "
            + predicate
            + " ORDER BY t.created_sequence DESC LIMIT 500",
        seq,
        seq,
        seq,
        p.workspaceId(),
        seq);
  }
}
