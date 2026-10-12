package com.lotline.shared;

import com.lotline.identity.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommandBus {
  public record Command(Member member, UUID id, UUID eventId, long sequence) {}

  private final Db db;
  private final Json json;

  public CommandBus(Db db, Json json) {
    this.db = db;
    this.json = json;
  }

  @Transactional
  @SuppressWarnings("unchecked")
  public Map<String, Object> run(
      Member p,
      String type,
      String key,
      Object request,
      String occurredAt,
      Function<Command, Map<String, Object>> action) {
    if (!type.startsWith("INBOX_READ:")) p.write();
    IdentityService.requireKey(key);
    db.one("SELECT id FROM workspaces WHERE id=? FOR UPDATE", p.workspaceId());
    var membership =
        db.one(
            "SELECT m.role,m.recall_manager,m.network_read FROM memberships m JOIN auth_sessions s"
                + " ON s.user_id=m.user_id AND s.workspace_id=m.workspace_id WHERE m.workspace_id=?"
                + " AND m.id=? AND s.id=? AND s.revoked_at IS NULL AND s.expires_at>now()",
            p.workspaceId(),
            p.membershipId(),
            p.sessionId());
    Set<UUID> currentSites = new HashSet<>();
    db.rows(
            "SELECT site_id FROM membership_sites WHERE workspace_id=? AND membership_id=?",
            p.workspaceId(),
            p.membershipId())
        .forEach(x -> currentSites.add(db.uuid(x.get("site_id"))));
    if (!p.role().equals(membership.get("role"))
        || p.recallManager() != Boolean.TRUE.equals(membership.get("recall_manager"))
        || p.networkRead() != Boolean.TRUE.equals(membership.get("network_read"))
        || !p.sites().equals(currentSites))
      throw new DomainError(
          409,
          "MEMBERSHIP_CHANGED",
          "Your permission grant changed. Refresh your membership before retrying.");
    String digest = Json.hash(json.write(request));
    var old =
        db.rows(
            "SELECT * FROM idempotency_records WHERE workspace_id=? AND actor_id=? AND scope=? AND"
                + " key=?",
            p.workspaceId(),
            p.userId(),
            type,
            key);
    if (!old.isEmpty()) {
      if (!digest.equals(old.getFirst().get("request_digest")))
        throw new DomainError(
            409,
            "IDEMPOTENCY_CONFLICT",
            "This key already accepted a different command. Use the original fields or a new key.");
      return (Map<String, Object>) old.getFirst().get("response");
    }
    long seq =
        db.count(
            "UPDATE workspaces SET sequence=sequence+1 WHERE id=? RETURNING sequence",
            p.workspaceId());
    UUID id = UUID.randomUUID(), event = UUID.randomUUID();
    Instant reported = occurredAt == null ? Instant.now() : Instant.parse(occurredAt);
    if (reported.isAfter(Instant.now().plusSeconds(300)))
      throw DomainError.invalid(
          "FUTURE_REPORT", "Reported time cannot be more than five minutes in the future.");
    db.update(
        "INSERT INTO"
            + " events(workspace_id,id,sequence,actor_id,organization_id,command_id,type,payload,occurred_at)"
            + " VALUES(?,?,?,?,?,?,?,?::jsonb,?)",
        p.workspaceId(),
        event,
        seq,
        p.userId(),
        p.organizationId(),
        id,
        type,
        json.write(request),
        Timestamp.from(reported));
    audience(new Command(p, id, event, seq), p.organizationId());
    Map<String, Object> result = new LinkedHashMap<>(action.apply(new Command(p, id, event, seq)));
    result.put("sequence", seq);
    result.put("commandId", id.toString());
    db.update(
        "INSERT INTO"
            + " idempotency_records(workspace_id,actor_id,scope,key,request_digest,response,status,command_id)"
            + " VALUES(?,?,?,?,?,?::jsonb,200,?)",
        p.workspaceId(),
        p.userId(),
        type,
        key,
        digest,
        json.write(result),
        id);
    return json.read(json.write(result));
  }

  public void audience(Command c, UUID org) {
    db.update(
        "INSERT INTO event_audience VALUES(?,?,?) ON CONFLICT DO NOTHING",
        c.member().workspaceId(),
        c.eventId(),
        org);
  }

  public void site(Command c, UUID site) {
    db.update(
        "INSERT INTO event_sites VALUES(?,?,?) ON CONFLICT DO NOTHING",
        c.member().workspaceId(),
        c.eventId(),
        site);
  }

  public void notify(Command c, UUID org, String message) {
    audience(c, org);
    db.update(
        "INSERT INTO outbox(workspace_id,id,event_id,recipient_id,message) SELECT"
            + " ?,gen_random_uuid(),?,m.user_id,? FROM memberships m WHERE m.workspace_id=? AND"
            + " (m.role='OWNER' OR m.recall_manager OR m.network_read OR (m.organization_id=? AND"
            + " (NOT EXISTS(SELECT 1 FROM membership_sites ms WHERE ms.workspace_id=m.workspace_id"
            + " AND ms.membership_id=m.id) OR NOT EXISTS(SELECT 1 FROM event_sites es WHERE"
            + " es.workspace_id=m.workspace_id AND es.event_id=?) OR EXISTS(SELECT 1 FROM"
            + " membership_sites ms JOIN event_sites es ON es.workspace_id=ms.workspace_id AND"
            + " es.site_id=ms.site_id WHERE ms.workspace_id=m.workspace_id AND"
            + " ms.membership_id=m.id AND es.event_id=?)))) ON"
            + " CONFLICT(workspace_id,event_id,recipient_id) DO NOTHING",
        c.member().workspaceId(),
        c.eventId(),
        message,
        c.member().workspaceId(),
        org,
        c.eventId(),
        c.eventId());
  }
}
