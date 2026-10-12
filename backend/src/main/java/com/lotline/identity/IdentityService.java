package com.lotline.identity;

import com.lotline.shared.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IdentityService {
  public record Register(
      @Email @NotBlank String email,
      @Size(min = 12, max = 72) String password,
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Size(max = 120) String workspaceName,
      @NotBlank @Size(max = 120) String organizationName) {}

  public record Login(@Email @NotBlank String email, @NotBlank String password) {}

  public record Accept(
      @NotBlank String token,
      @Email @NotBlank String email,
      @Size(min = 12, max = 72) String password,
      @NotBlank @Size(max = 120) String name) {}

  public record Session(
      String accessToken, Map<String, Object> user, List<Map<String, Object>> memberships) {}

  public record Issued(Session body, String refresh) {}

  private final Db db;
  private final Json json;
  private final SecretKey secret;
  private final TransactionTemplate tx;
  private final AuthRateGate rate;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
  private final String dummy = passwords.encode("timing-only-placeholder");

  public IdentityService(
      Db db,
      Json json,
      PlatformTransactionManager manager,
      AuthRateGate rate,
      @Value("${lotline.jwt-secret}") String key) {
    if (key.length() < 32)
      throw new IllegalStateException("JWT_SECRET must contain at least 32 characters");
    this.db = db;
    this.json = json;
    this.rate = rate;
    this.secret = Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8));
    this.tx = new TransactionTemplate(manager);
  }

  public static String token() {
    byte[] b = new byte[48];
    new SecureRandom().nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  public String invitationToken(UUID workspace, UUID invitation) {
    try {
      var mac = javax.crypto.Mac.getInstance("HmacSHA384");
      mac.init(new javax.crypto.spec.SecretKeySpec(secret.getEncoded(), "HmacSHA384"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(
              mac.doFinal(
                  ("invitation:" + workspace + ":" + invitation).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public Issued register(Register r, String key) {
    requireKey(key);
    return tx.execute(
        s -> {
          String email = r.email().trim().toLowerCase(Locale.ROOT);
          db.jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class, email);
          String digest = commandDigest(json.write(r));
          var old =
              db.rows("SELECT * FROM onboarding_commands WHERE email=? AND key=?", email, key);
          if (!old.isEmpty()) {
            if (!digest.equals(old.getFirst().get("digest")))
              throw new DomainError(
                  409,
                  "IDEMPOTENCY_CONFLICT",
                  "This onboarding key was used with different fields.");
            return issue(
                db.uuid(old.getFirst().get("user_id")),
                db.uuid(old.getFirst().get("workspace_id")));
          }
          if (db.count("SELECT count(*) FROM users WHERE email=?", email) > 0)
            throw new DomainError(
                409,
                "EMAIL_EXISTS",
                "This email already has an account. Sign in or accept an invitation.");
          UUID u = UUID.randomUUID(),
              w = UUID.randomUUID(),
              o = UUID.randomUUID(),
              m = UUID.randomUUID();
          db.update(
              "INSERT INTO users(id,email,name,password_hash) VALUES(?,?,?,?)",
              u,
              email,
              r.name(),
              passwords.encode(r.password()));
          db.update("INSERT INTO workspaces(id,name,sequence) VALUES(?,?,1)", w, r.workspaceName());
          db.update(
              "INSERT INTO organizations(workspace_id,id,name) VALUES(?,?,?)",
              w,
              o,
              r.organizationName());
          db.update(
              "INSERT INTO memberships(workspace_id,id,user_id,organization_id,role)"
                  + " VALUES(?,?,?,?,'OWNER')",
              w,
              m,
              u,
              o);
          UUID event = UUID.randomUUID();
          db.update(
              "INSERT INTO"
                  + " events(workspace_id,id,sequence,actor_id,organization_id,command_id,type,payload,occurred_at)"
                  + " VALUES(?,?,1,?,?,?,'WORKSPACE_CREATED',?::jsonb,now())",
              w,
              event,
              u,
              o,
              UUID.randomUUID(),
              json.write(
                  Map.of(
                      "workspaceName",
                      r.workspaceName(),
                      "organizationName",
                      r.organizationName(),
                      "role",
                      "OWNER")));
          db.update("INSERT INTO event_audience VALUES(?,?,?)", w, event, o);
          db.update(
              "INSERT INTO onboarding_commands(email,key,digest,user_id,workspace_id)"
                  + " VALUES(?,?,?,?,?)",
              email,
              key,
              digest,
              u,
              w);
          return issue(u, w);
        });
  }

  public Issued login(Login r) {
    rate.check(r.email().trim().toLowerCase(Locale.ROOT));
    return tx.execute(
        s -> {
          var users =
              db.rows(
                  "SELECT * FROM users WHERE email=?", r.email().trim().toLowerCase(Locale.ROOT));
          String hash = users.isEmpty() ? dummy : users.getFirst().get("password_hash").toString();
          if (!passwords.matches(r.password(), hash) || users.isEmpty())
            throw new DomainError(401, "INVALID_CREDENTIALS", "Email or password is incorrect.");
          UUID user = db.uuid(users.getFirst().get("id"));
          UUID w =
              db.uuid(
                  db.one(
                          "SELECT workspace_id FROM memberships WHERE user_id=? ORDER BY"
                              + " workspace_id LIMIT 1",
                          user)
                      .get("workspace_id"));
          return issue(user, w);
        });
  }

  private String commandDigest(String value) {
    try {
      var mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new javax.crypto.spec.SecretKeySpec(secret.getEncoded(), "HmacSHA256"));
      return "hmac-v1:"
          + java.util.HexFormat.of()
              .formatHex(mac.doFinal(("onboarding:" + value).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public Issued accept(Accept r) {
    return tx.execute(
        s -> {
          var invitations =
              db.rows(
                  "SELECT * FROM invitations WHERE token_hash=? FOR UPDATE", Json.hash(r.token()));
          if (invitations.isEmpty())
            throw new DomainError(
                410, "INVITATION_EXPIRED", "This invitation is invalid, expired or already used.");
          var inv = invitations.getFirst();
          if (inv.get("used_at") != null
              || Instant.parse(
                      inv.get("expires_at").toString().replace(" ", "T").replace("+00", "Z"))
                  .isBefore(Instant.now()))
            throw new DomainError(
                410,
                "INVITATION_EXPIRED",
                "This invitation is expired or already used. Ask the workspace owner for another"
                    + " link.");
          String email = r.email().trim().toLowerCase(Locale.ROOT);
          if (!email.equals(inv.get("email"))) throw DomainError.forbidden();
          UUID w = db.uuid(inv.get("workspace_id"));
          db.one("SELECT id FROM workspaces WHERE id=? FOR UPDATE", w);
          var users = db.rows("SELECT * FROM users WHERE email=?", email);
          UUID user;
          if (users.isEmpty()) {
            user = UUID.randomUUID();
            db.update(
                "INSERT INTO users(id,email,name,password_hash) VALUES(?,?,?,?)",
                user,
                email,
                r.name(),
                passwords.encode(r.password()));
          } else {
            var u = users.getFirst();
            if (!passwords.matches(r.password(), u.get("password_hash").toString()))
              throw new DomainError(
                  401,
                  "INVALID_CREDENTIALS",
                  "Use your existing account password to accept this invitation.");
            user = db.uuid(u.get("id"));
          }
          UUID m = UUID.randomUUID(),
              org = db.uuid(inv.get("organization_id")),
              id = db.uuid(inv.get("id"));
          if (db.count(
                  "SELECT count(*) FROM memberships WHERE workspace_id=? AND user_id=?", w, user)
              > 0)
            throw new DomainError(
                409, "ALREADY_MEMBER", "This account already belongs to the workspace.");
          db.update(
              "INSERT INTO"
                  + " memberships(workspace_id,id,user_id,organization_id,role,recall_manager,network_read)"
                  + " VALUES(?,?,?,?,?,?,?)",
              w,
              m,
              user,
              org,
              inv.get("role"),
              inv.get("recall_manager"),
              inv.get("network_read"));
          db.update(
              "INSERT INTO membership_sites SELECT workspace_id,?,site_id FROM invitation_sites"
                  + " WHERE workspace_id=? AND invitation_id=?",
              m,
              w,
              id);
          db.update("UPDATE invitations SET used_at=now() WHERE workspace_id=? AND id=?", w, id);
          long seq =
              db.count(
                  "UPDATE workspaces SET sequence=sequence+1 WHERE id=? RETURNING sequence", w);
          UUID event = UUID.randomUUID();
          db.update(
              "INSERT INTO"
                  + " events(workspace_id,id,sequence,actor_id,organization_id,command_id,type,payload,occurred_at)"
                  + " VALUES(?,?,?,?,?,?,'INVITATION_ACCEPTED',?::jsonb,now())",
              w,
              event,
              seq,
              user,
              org,
              UUID.randomUUID(),
              json.write(Map.of("membershipId", m.toString(), "role", inv.get("role"))));
          db.update("INSERT INTO event_audience VALUES(?,?,?)", w, event, org);
          return issue(user, w);
        });
  }

  private Issued issue(UUID user, UUID workspace) {
    UUID id = UUID.randomUUID();
    String refresh = token();
    db.update(
        "INSERT INTO auth_sessions(id,user_id,workspace_id,refresh_hash,expires_at)"
            + " VALUES(?,?,?,?,now()+interval '14 days')",
        id,
        user,
        workspace,
        Json.hash(refresh));
    return new Issued(body(user, id), refresh);
  }

  public Issued refresh(String raw) {
    if (raw == null) throw new DomainError(401, "SESSION_REQUIRED", "Sign in to continue.");
    Object result =
        tx.execute(
            s -> {
              String hash = Json.hash(raw);
              var rows =
                  db.rows("SELECT * FROM auth_sessions WHERE refresh_hash=? FOR UPDATE", hash);
              if (rows.isEmpty()) {
                var used =
                    db.rows("SELECT session_id FROM used_refresh_tokens WHERE token_hash=?", hash);
                if (!used.isEmpty())
                  db.update(
                      "UPDATE auth_sessions SET revoked_at=now() WHERE id=?",
                      db.uuid(used.getFirst().get("session_id")));
                return new DomainError(
                    401,
                    "SESSION_REPLAY",
                    "This session was revoked after a rotated token was reused. Sign in again.");
              }
              var row = rows.getFirst();
              UUID id = db.uuid(row.get("id"));
              if (row.get("revoked_at") != null
                  || db.count(
                          "SELECT count(*) FROM auth_sessions WHERE id=? AND expires_at>now()", id)
                      == 0)
                return new DomainError(
                    401, "SESSION_EXPIRED", "Your session expired. Sign in again.");
              String next = token();
              db.update(
                  "INSERT INTO used_refresh_tokens(token_hash,session_id) VALUES(?,?)", hash, id);
              db.update(
                  "UPDATE auth_sessions SET refresh_hash=?,expires_at=now()+interval '14 days'"
                      + " WHERE id=?",
                  Json.hash(next),
                  id);
              return new Issued(body(db.uuid(row.get("user_id")), id), next);
            });
    if (result instanceof DomainError e) throw e;
    return (Issued) result;
  }

  public void logout(String raw) {
    if (raw != null)
      db.update("UPDATE auth_sessions SET revoked_at=now() WHERE refresh_hash=?", Json.hash(raw));
  }

  public Session switchWorkspace(Member p, UUID workspace) {
    return tx.execute(
        s -> {
          if (db.count(
                  "SELECT count(*) FROM memberships WHERE user_id=? AND workspace_id=?",
                  p.userId(),
                  workspace)
              == 0) throw DomainError.forbidden();
          db.update(
              "UPDATE auth_sessions SET workspace_id=? WHERE id=? AND user_id=?",
              workspace,
              p.sessionId(),
              p.userId());
          return body(p.userId(), p.sessionId());
        });
  }

  public Session body(UUID user, UUID session) {
    String access =
        Jwts.builder()
            .subject(user.toString())
            .claim("sid", session.toString())
            .issuer("lotline")
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now().plusSeconds(900)))
            .signWith(secret)
            .compact();
    return new Session(
        access,
        db.one("SELECT id,email,name FROM users WHERE id=?", user),
        db.rows(
            "SELECT m.workspace_id,w.name workspace_name,m.organization_id,o.name"
                + " organization_name,m.role,m.recall_manager,m.network_read,s.workspace_id=m.workspace_id"
                + " active FROM memberships m JOIN workspaces w ON w.id=m.workspace_id JOIN"
                + " organizations o ON o.workspace_id=m.workspace_id AND o.id=m.organization_id"
                + " JOIN auth_sessions s ON s.id=? WHERE m.user_id=? ORDER BY w.name",
            session,
            user));
  }

  public Member authenticate(String access) {
    try {
      var c =
          Jwts.parser()
              .verifyWith(secret)
              .requireIssuer("lotline")
              .build()
              .parseSignedClaims(access)
              .getPayload();
      UUID u = UUID.fromString(c.getSubject()), sid = UUID.fromString(c.get("sid", String.class));
      var r =
          db.one(
              "SELECT m.*,s.id session_id FROM auth_sessions s JOIN memberships m ON"
                  + " m.workspace_id=s.workspace_id AND m.user_id=s.user_id WHERE s.id=? AND"
                  + " s.user_id=? AND s.revoked_at IS NULL AND s.expires_at>now()",
              sid,
              u);
      UUID w = db.uuid(r.get("workspace_id")), m = db.uuid(r.get("id"));
      Set<UUID> sites = new HashSet<>();
      db.rows("SELECT site_id FROM membership_sites WHERE workspace_id=? AND membership_id=?", w, m)
          .forEach(x -> sites.add(db.uuid(x.get("site_id"))));
      return new Member(
          u,
          sid,
          w,
          m,
          db.uuid(r.get("organization_id")),
          r.get("role").toString(),
          Boolean.TRUE.equals(r.get("recall_manager")),
          sites,
          Boolean.TRUE.equals(r.get("network_read")));
    } catch (Exception e) {
      throw new DomainError(
          401,
          "SESSION_REQUIRED",
          "Your access token expired or was revoked. Refresh your session or sign in.");
    }
  }

  public static void requireKey(String key) {
    if (key == null || !key.matches("[A-Za-z0-9._:-]{8,128}"))
      throw new DomainError(
          400,
          "IDEMPOTENCY_KEY_REQUIRED",
          "Provide an Idempotency-Key of 8–128 letters, digits, dots, colons, dashes or"
              + " underscores.");
  }
}
