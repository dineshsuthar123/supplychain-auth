package com.lotline.audit;

import com.lotline.identity.Member;
import com.lotline.lineage.TraceService;
import com.lotline.recall.RecallService;
import com.lotline.shared.*;
import com.lotline.workspace.Scope;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class EvidenceService {
  private final Db db;
  private final Json json;
  private final TraceService trace;
  private final Scope scope;
  private final RecallService recalls;

  public EvidenceService(Db db, Json json, TraceService trace, Scope scope, RecallService recalls) {
    this.db = db;
    this.json = json;
    this.trace = trace;
    this.scope = scope;
    this.recalls = recalls;
  }

  public static String escape(Object value) {
    return value == null
        ? ""
        : value
            .toString()
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
  }

  public static String csv(List<Map<String, Object>> rows) {
    if (rows.isEmpty()) return "\n";
    var keys = new ArrayList<>(rows.getFirst().keySet());
    StringBuilder s = new StringBuilder();
    s.append(String.join(",", keys)).append('\n');
    for (var r : rows) {
      for (int i = 0; i < keys.size(); i++) {
        if (i > 0) s.append(',');
        String v = Objects.toString(r.get(keys.get(i)), "");
        if (v.matches("^[=+@-].*")) v = "'" + v;
        s.append('"').append(v.replace("\"", "\"\"")).append('"');
      }
      s.append('\n');
    }
    return s.toString();
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  @SuppressWarnings("unchecked")
  public byte[] export(Member p, Long requested) {
    long seq = trace.sequence(p, requested);
    var graph = trace.graph(p, seq);
    if (!Boolean.TRUE.equals(graph.get("complete")))
      throw DomainError.invalid(
          "INCOMPLETE_EXPORT",
          "The graph exceeds export bounds; narrow the workspace or request a smaller historical"
              + " sequence.");
    var events =
        db.rows(
            "SELECT e.*,u.name actor_name,o.name organization_name FROM events e JOIN users u ON"
                + " u.id=e.actor_id JOIN organizations o ON o.workspace_id=e.workspace_id AND"
                + " o.id=e.organization_id WHERE e.workspace_id=? AND e.sequence<=? AND "
                + scope.eventPredicate(p, "e")
                + " ORDER BY e.sequence LIMIT 20001",
            p.workspaceId(),
            seq);
    var journal =
        db.rows(
            "SELECT j.* FROM inventory_journal j JOIN sites s ON s.workspace_id=j.workspace_id AND"
                + " s.id=j.site_id WHERE j.workspace_id=? AND j.sequence<=? AND "
                + scope.journalPredicate(p, "j", "s")
                + " ORDER BY j.sequence,j.id LIMIT 50001",
            p.workspaceId(),
            seq);
    if (events.size() > 20000 || journal.size() > 50000)
      throw DomainError.invalid(
          "EXPORT_BOUND",
          "Export supports at most 20,000 events and 50,000 journal entries per snapshot.");
    List<Map<String, Object>> incidents = new ArrayList<>();
    for (var r : recalls.list(p))
      if (((Number) r.get("activation_sequence")).longValue() <= seq) {
        Map<String, Object> item = new LinkedHashMap<>();
        var prior =
            db.rows(
                "SELECT * FROM recall_revisions WHERE workspace_id=? AND recall_id=? AND"
                    + " sequence<=? ORDER BY revision",
                p.workspaceId(),
                db.uuid(r.get("id")),
                seq);
        var incident = new LinkedHashMap<>(r);
        if (!prior.isEmpty()) {
          var last = prior.getLast();
          incident.put("version", last.get("revision"));
          incident.put(
              "status", last.get("reason").toString().startsWith("Closure:") ? "CLOSED" : "ACTIVE");
        }
        item.put("incident", incident);
        item.put(
            "revisions",
            p.network()
                ? prior
                : db.rows(
                    "SELECT revision,sequence,'Scope revised; inspect your current target rows.'"
                        + " reason FROM recall_revisions WHERE workspace_id=? AND recall_id=? AND"
                        + " sequence<=? ORDER BY revision",
                    p.workspaceId(),
                    db.uuid(r.get("id")),
                    seq));
        incidents.add(item);
      }
    var responses =
        db.rows(
            "SELECT a.*,u.name actor_name FROM acknowledgments a JOIN users u ON u.id=a.actor_id"
                + " WHERE a.workspace_id=? AND a.sequence<=? AND "
                + (p.network() ? "true" : "a.organization_id='" + p.organizationId() + "'"),
            p.workspaceId(),
            seq);
    Map<String, byte[]> files = new LinkedHashMap<>();
    put(
        files,
        "events.json",
        json.write(Map.of("schemaVersion", 1, "sequence", seq, "events", events)));
    put(
        files,
        "journal.json",
        json.write(Map.of("schemaVersion", 1, "sequence", seq, "entries", journal)));
    put(files, "trace.json", json.write(graph));
    put(files, "incidents.json", json.write(Map.of("schemaVersion", 1, "incidents", incidents)));
    put(
        files,
        "exposure.csv",
        csv(
            ((List<Map<String, Object>>) graph.get("holdings"))
                .stream().filter(h -> !"CONSUMED".equals(h.get("status"))).toList()));
    put(files, "responses.csv", csv(responses));
    StringBuilder html =
        new StringBuilder(
            "<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><title>Lotline"
                + " evidence</title><style>body{font:16px system-ui;max-width:1100px;margin:40px"
                + " auto;color:#172e28}table{width:100%;border-collapse:collapse}td,th{text-align:left;border-bottom:1px"
                + " solid #ccc;padding:8px}pre{white-space:pre-wrap;overflow-wrap:anywhere}@media"
                + " print{body{margin:10mm}}</style><h1>Lotline · evidence"
                + " snapshot</h1><p>Workspace "
                + p.workspaceId()
                + " · committed sequence "
                + seq
                + " · "
                + escape(graph.get("scope"))
                + "</p><p>Quantities are exact decimal strings in each product's named base unit."
                + " Consumed input quantities are historical and excluded from current exposure."
                + " Unresolved stock is a custody gap. Partner assertions and unreported events are"
                + " not independently verified.</p><h2>Current"
                + " exposure</h2><table><tr><th>Lot</th><th>Organization /"
                + " site</th><th>Status</th><th>Quantity / unit</th></tr>");
    for (var h : (List<Map<String, Object>>) graph.get("holdings"))
      if (!"CONSUMED".equals(h.get("status")))
        html.append("<tr><td>")
            .append(escape(h.get("code")))
            .append("</td><td>")
            .append(escape(h.get("organization_name")))
            .append(" / ")
            .append(escape(h.get("site_name")))
            .append("</td><td>")
            .append(escape(h.get("status")))
            .append("</td><td>")
            .append(escape(h.get("quantity")))
            .append(" ")
            .append(escape(h.get("base_unit")))
            .append("</td></tr>");
    html.append("</table><h2>Accepted event timeline</h2>");
    for (var e : events)
      html.append("<article><h3>")
          .append(escape(e.get("sequence")))
          .append(" · ")
          .append(escape(e.get("type")))
          .append("</h3><p>Asserted by ")
          .append(escape(e.get("actor_name")))
          .append(" / ")
          .append(escape(e.get("organization_name")))
          .append("<br>Reported: ")
          .append(escape(e.get("occurred_at")))
          .append(" · Recorded: ")
          .append(escape(e.get("recorded_at")))
          .append("</p><pre>")
          .append(escape(json.write(e.get("payload"))))
          .append("</pre></article>");
    html.append("<h2>Recall revisions and responses</h2><pre>")
        .append(escape(json.write(incidents)))
        .append("\n")
        .append(escape(json.write(responses)))
        .append(
            "</pre><p>SHA-256 hashes detect changed snapshot bytes. They do not prove physical"
                + " authenticity or provide an external trust anchor.</p></html>");
    put(files, "report.html", html.toString());
    Map<String, Object> hashes = new LinkedHashMap<>();
    files.forEach(
        (name, bytes) ->
            hashes.put(
                name,
                Map.of("sha256", Json.hash(bytes), "bytes", bytes.length, "schemaVersion", 1)));
    put(
        files,
        "manifest.json",
        json.write(
            Map.of(
                "schemaVersion",
                1,
                "workspaceId",
                p.workspaceId().toString(),
                "sequence",
                seq,
                "scope",
                graph.get("scope"),
                "files",
                hashes)));
    try (var output = new ByteArrayOutputStream();
        var zip = new ZipOutputStream(output)) {
      for (var file : files.entrySet()) {
        var entry = new ZipEntry(file.getKey());
        entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(file.getValue());
        zip.closeEntry();
      }
      zip.finish();
      return output.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private void put(Map<String, byte[]> files, String name, String value) {
    files.put(name, value.getBytes(StandardCharsets.UTF_8));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Map<String, Object> compareProjection(Member p) {
    p.manageRecall();
    var differences =
        db.rows(
            "WITH rebuilt AS (SELECT workspace_id,lot_id,site_id,status,transfer_id,sum(delta)"
                + " quantity FROM inventory_journal WHERE workspace_id=? GROUP BY"
                + " workspace_id,lot_id,site_id,status,transfer_id) SELECT"
                + " COALESCE(r.lot_id,b.lot_id) lot_id,COALESCE(r.site_id,b.site_id)"
                + " site_id,COALESCE(r.status,b.status) status,COALESCE(r.quantity,0)"
                + " journal_quantity,COALESCE(b.quantity,0) live_quantity FROM rebuilt r FULL OUTER"
                + " JOIN (SELECT * FROM balances WHERE workspace_id=?) b ON"
                + " r.workspace_id=b.workspace_id AND r.lot_id=b.lot_id AND r.site_id=b.site_id AND"
                + " r.status=b.status AND r.transfer_id IS NOT DISTINCT FROM b.transfer_id WHERE"
                + " COALESCE(r.quantity,0)<>COALESCE(b.quantity,0)",
            p.workspaceId(),
            p.workspaceId());
    return Map.of(
        "sequence",
        db.count("SELECT sequence FROM workspaces WHERE id=?", p.workspaceId()),
        "matches",
        differences.isEmpty(),
        "differences",
        differences);
  }

  private final String rebuildSql =
      "INSERT INTO balances(workspace_id,lot_id,site_id,status,transfer_id,quantity) SELECT"
          + " workspace_id,lot_id,site_id,status,transfer_id,sum(delta) FROM inventory_journal"
          + " WHERE workspace_id=? GROUP BY workspace_id,lot_id,site_id,status,transfer_id HAVING"
          + " sum(delta)<>0";

  public Map<String, Object> rebuild(Member p, String key, CommandBus bus) {
    p.manageRecall();
    return bus.run(
        p,
        "PROJECTION_REBUILT",
        key,
        Map.of("workspaceId", p.workspaceId().toString()),
        null,
        c -> {
          db.update("DELETE FROM balances WHERE workspace_id=?", p.workspaceId());
          db.update(rebuildSql, p.workspaceId());
          return Map.of("matches", true);
        });
  }
}
