package com.lotline.workspace;

import com.lotline.identity.*;
import com.lotline.shared.*;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceService {
  public record Organization(@NotBlank @Size(max = 160) String name) {}

  public record Site(@NotNull UUID organizationId, @NotBlank @Size(max = 160) String name) {}

  public record Invite(
      @NotNull UUID organizationId,
      @Email @NotBlank String email,
      @NotBlank String role,
      boolean recallManager,
      List<UUID> siteIds,
      boolean networkRead) {
    public Invite(
        UUID organizationId, String email, String role, boolean recallManager, List<UUID> siteIds) {
      this(organizationId, email, role, recallManager, siteIds, false);
    }
  }

  public record Permissions(
      @NotNull UUID membershipId,
      @NotBlank String role,
      boolean recallManager,
      List<UUID> siteIds,
      boolean networkRead) {
    public Permissions(UUID membershipId, String role, boolean recallManager, List<UUID> siteIds) {
      this(membershipId, role, recallManager, siteIds, false);
    }
  }

  private final Db db;
  private final CommandBus bus;
  private final Scope scope;
  private final IdentityService identity;

  public WorkspaceService(Db db, CommandBus bus, Scope scope, IdentityService identity) {
    this.db = db;
    this.bus = bus;
    this.scope = scope;
    this.identity = identity;
  }

  public Map<String, Object> get(Member p) {
    return Map.of(
        "workspace",
        db.one("SELECT id,name,sequence FROM workspaces WHERE id=?", p.workspaceId()),
        "organizations",
        db.rows(
            "SELECT id,name FROM organizations WHERE workspace_id=? ORDER BY name",
            p.workspaceId()),
        "sites",
        db.rows(
            "SELECT s.*,o.name organization_name FROM sites s JOIN organizations o ON"
                + " o.workspace_id=s.workspace_id AND o.id=s.organization_id WHERE s.workspace_id=?"
                + " ORDER BY o.name,s.name",
            p.workspaceId()),
        "members",
        p.owner()
            ? db.rows(
                "SELECT"
                    + " m.id,m.organization_id,m.role,m.recall_manager,m.network_read,u.name,u.email,COALESCE((SELECT"
                    + " array_agg(site_id) FROM membership_sites ms WHERE"
                    + " ms.workspace_id=m.workspace_id AND ms.membership_id=m.id),'{}') site_ids"
                    + " FROM memberships m JOIN users u ON u.id=m.user_id WHERE m.workspace_id=?"
                    + " ORDER BY u.name",
                p.workspaceId())
            : List.of(),
        "invitations",
        p.owner()
            ? db.rows(
                "SELECT id,organization_id,email,role,expires_at,used_at FROM invitations WHERE"
                    + " workspace_id=? ORDER BY expires_at DESC LIMIT 100",
                p.workspaceId())
            : List.of());
  }

  public Map<String, Object> organization(Member p, String k, Organization r) {
    p.administer();
    return bus.run(
        p,
        "ORGANIZATION_CREATED",
        k,
        r,
        null,
        c -> {
          UUID id = UUID.randomUUID();
          db.update("INSERT INTO organizations VALUES(?,?,?)", p.workspaceId(), id, r.name());
          return Map.of("id", id.toString());
        });
  }

  public Map<String, Object> site(Member p, String k, Site r) {
    p.administer();
    return bus.run(
        p,
        "SITE_CREATED",
        k,
        r,
        null,
        c -> {
          scope.organization(p, r.organizationId());
          UUID id = UUID.randomUUID();
          db.update(
              "INSERT INTO sites VALUES(?,?,?,?)",
              p.workspaceId(),
              id,
              r.organizationId(),
              r.name());
          return Map.of("id", id.toString());
        });
  }

  public Map<String, Object> invite(Member p, String k, Invite r) {
    p.administer();
    Map<String, Object> result =
        bus.run(
            p,
            "INVITATION_CREATED",
            k,
            r,
            null,
            c -> {
              if (!Set.of("OPERATOR", "PARTNER_OPERATOR", "AUDITOR").contains(r.role())
                  || (r.recallManager() && "AUDITOR".equals(r.role()))
                  || (r.networkRead() && !"AUDITOR".equals(r.role())))
                throw DomainError.invalid(
                    "INVITATION_ROLE",
                    "Choose a bounded operator or read-only auditor role. Full network read access"
                        + " is an explicit auditor grant.");
              scope.organization(p, r.organizationId());
              UUID id = UUID.randomUUID();
              String raw = identity.invitationToken(p.workspaceId(), id);
              db.update(
                  "INSERT INTO"
                      + " invitations(workspace_id,id,organization_id,email,role,recall_manager,token_hash,expires_at,network_read)"
                      + " VALUES(?,?,?,?,?,?,?,now()+interval '7 days',?)",
                  p.workspaceId(),
                  id,
                  r.organizationId(),
                  r.email().toLowerCase(Locale.ROOT),
                  r.role(),
                  r.recallManager(),
                  Json.hash(raw),
                  r.networkRead());
              for (UUID site : r.siteIds() == null ? List.<UUID>of() : r.siteIds()) {
                var s =
                    db.one(
                        "SELECT organization_id FROM sites WHERE workspace_id=? AND id=?",
                        p.workspaceId(),
                        site);
                if (!r.organizationId().toString().equals(s.get("organization_id")))
                  throw DomainError.forbidden();
                db.update("INSERT INTO invitation_sites VALUES(?,?,?)", p.workspaceId(), id, site);
              }
              return Map.of("id", id.toString());
            });
    var full = new LinkedHashMap<>(result);
    full.put(
        "invitationPath",
        "/invite?token="
            + identity.invitationToken(
                p.workspaceId(), UUID.fromString(result.get("id").toString())));
    return full;
  }

  public Map<String, Object> permissions(Member p, String k, Permissions r) {
    p.administer();
    return bus.run(
        p,
        "MEMBERSHIP_CHANGED",
        k,
        r,
        null,
        c -> {
          var member =
              db.one(
                  "SELECT * FROM memberships WHERE workspace_id=? AND id=?",
                  p.workspaceId(),
                  r.membershipId());
          if ("OWNER".equals(member.get("role"))
              || !Set.of("OPERATOR", "PARTNER_OPERATOR", "AUDITOR").contains(r.role())
              || ("AUDITOR".equals(r.role()) && r.recallManager())
              || (r.networkRead() && !"AUDITOR".equals(r.role())))
            throw DomainError.invalid(
                "BOUNDED_ROLE",
                "The workspace creator remains owner. Network read is an explicit read-only auditor"
                    + " grant; recall management is a separate operator permission.");
          db.update(
              "UPDATE memberships SET role=?,recall_manager=?,network_read=? WHERE workspace_id=?"
                  + " AND id=?",
              r.role(),
              r.recallManager(),
              r.networkRead(),
              p.workspaceId(),
              r.membershipId());
          db.update(
              "DELETE FROM membership_sites WHERE workspace_id=? AND membership_id=?",
              p.workspaceId(),
              r.membershipId());
          for (UUID id : r.siteIds() == null ? List.<UUID>of() : r.siteIds()) {
            var site =
                db.one(
                    "SELECT organization_id FROM sites WHERE workspace_id=? AND id=?",
                    p.workspaceId(),
                    id);
            if (!site.get("organization_id").equals(member.get("organization_id")))
              throw DomainError.forbidden();
            db.update(
                "INSERT INTO membership_sites VALUES(?,?,?)",
                p.workspaceId(),
                r.membershipId(),
                id);
          }
          return Map.of("id", r.membershipId().toString());
        });
  }
}
