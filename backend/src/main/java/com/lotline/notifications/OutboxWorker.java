package com.lotline.notifications;

import com.lotline.shared.Db;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OutboxWorker {
  private final Db db;
  private final TransactionTemplate tx;

  public OutboxWorker(Db db, PlatformTransactionManager manager) {
    this.db = db;
    this.tx = new TransactionTemplate(manager);
  }

  public List<Map<String, Object>> claim() {
    return tx.execute(
        s ->
            db.rows(
                "WITH due AS (SELECT workspace_id,id FROM outbox WHERE delivered_at IS NULL AND"
                    + " next_attempt_at<=now() AND (lease_until IS NULL OR lease_until<now()) AND"
                    + " attempts<12 ORDER BY next_attempt_at,id FOR UPDATE SKIP LOCKED LIMIT 50)"
                    + " UPDATE outbox o SET lease_until=now()+interval '30"
                    + " seconds',attempts=o.attempts+1 FROM due WHERE"
                    + " o.workspace_id=due.workspace_id AND o.id=due.id RETURNING o.*"));
  }

  public void deliver(UUID workspace, UUID id) {
    tx.executeWithoutResult(
        s -> {
          var r =
              db.one(
                  "SELECT * FROM outbox WHERE workspace_id=? AND id=? FOR UPDATE", workspace, id);
          if (r.get("delivered_at") != null) return;
          db.update(
              "INSERT INTO inbox(workspace_id,id,event_id,recipient_id,message) VALUES(?,?,?,?,?)"
                  + " ON CONFLICT(workspace_id,event_id,recipient_id) DO NOTHING",
              workspace,
              id,
              db.uuid(r.get("event_id")),
              db.uuid(r.get("recipient_id")),
              r.get("message"));
          db.update(
              "UPDATE outbox SET delivered_at=now(),lease_until=NULL,last_error=NULL WHERE"
                  + " workspace_id=? AND id=?",
              workspace,
              id);
        });
  }

  @Scheduled(fixedDelayString = "${lotline.outbox-delay-ms}")
  public void tick() {
    for (var r : claim()) {
      UUID w = db.uuid(r.get("workspace_id")), id = db.uuid(r.get("id"));
      try {
        deliver(w, id);
      } catch (RuntimeException e) {
        db.update(
            "UPDATE outbox SET"
                + " lease_until=NULL,next_attempt_at=now()+least(3600,power(2,attempts)::integer)*interval"
                + " '1 second',last_error='Local inbox delivery failed; retry scheduled' WHERE"
                + " workspace_id=? AND id=? AND delivered_at IS NULL",
            w,
            id);
      }
    }
  }
}
