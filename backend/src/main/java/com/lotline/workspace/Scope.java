package com.lotline.workspace;

import com.lotline.identity.Member;
import com.lotline.shared.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class Scope {
  private final Db db;

  public Scope(Db db) {
    this.db = db;
  }

  public Map<String, Object> site(Member p, UUID id, boolean mutation) {
    if (mutation) p.write();
    var row = db.one("SELECT * FROM sites WHERE workspace_id=? AND id=?", p.workspaceId(), id);
    if (!p.owner()
        && (!p.organizationId().toString().equals(row.get("organization_id"))
            || (!p.sites().isEmpty() && !p.sites().contains(id)))) throw DomainError.forbidden();
    return row;
  }

  public boolean seesSite(Member p, Object site, Object org) {
    return p.network()
        || (p.organizationId().toString().equals(org.toString())
            && (p.sites().isEmpty() || p.sites().contains(UUID.fromString(site.toString()))));
  }

  public String sitePredicate(Member p, String alias) {
    return p.network()
        ? "true"
        : alias
            + ".organization_id='"
            + p.organizationId()
            + "'"
            + (p.sites().isEmpty()
                ? ""
                : " AND "
                    + alias
                    + ".id IN ("
                    + p.sites().stream()
                        .sorted()
                        .map(x -> "'" + x + "'")
                        .reduce((a, b) -> a + "," + b)
                        .orElse("NULL")
                    + ")");
  }

  public void organization(Member p, UUID org) {
    db.one("SELECT id FROM organizations WHERE workspace_id=? AND id=?", p.workspaceId(), org);
  }

  public void lot(Member p, UUID lot) {
    db.one("SELECT id FROM lots WHERE workspace_id=? AND id=?", p.workspaceId(), lot);
  }

  public String eventPredicate(Member p, String alias) {
    if (p.network()) return "true";
    String base =
        "EXISTS(SELECT 1 FROM event_audience a WHERE a.workspace_id="
            + alias
            + ".workspace_id AND a.event_id="
            + alias
            + ".id AND a.organization_id='"
            + p.organizationId()
            + "')";
    if (p.sites().isEmpty()) return base;
    return base
        + " AND (NOT EXISTS(SELECT 1 FROM event_sites es WHERE es.workspace_id="
        + alias
        + ".workspace_id AND es.event_id="
        + alias
        + ".id) OR EXISTS(SELECT 1 FROM event_sites es JOIN sites s ON"
        + " s.workspace_id=es.workspace_id AND s.id=es.site_id WHERE es.workspace_id="
        + alias
        + ".workspace_id AND es.event_id="
        + alias
        + ".id AND "
        + sitePredicate(p, "s")
        + "))";
  }

  public String journalPredicate(Member p, String journal, String site) {
    if (p.network()) return "true";
    return "(("
        + sitePredicate(p, site)
        + ") OR ("
        + journal
        + ".status IN ('IN_TRANSIT','UNRESOLVED','LOST') AND EXISTS(SELECT 1 FROM transfers claim"
        + " JOIN sites origin_site ON origin_site.workspace_id=claim.workspace_id AND"
        + " origin_site.id=claim.source_site_id WHERE claim.workspace_id="
        + journal
        + ".workspace_id AND claim.id="
        + journal
        + ".transfer_id AND "
        + sitePredicate(p, "origin_site")
        + ")))";
  }
}
