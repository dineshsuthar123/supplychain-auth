package com.lotline.notifications;

import com.lotline.identity.Member;
import com.lotline.shared.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inbox")
public class InboxController {
  private final Db db;
  private final CommandBus bus;

  public InboxController(Db db, CommandBus bus) {
    this.db = db;
    this.bus = bus;
  }

  @GetMapping
  public Object list(@AuthenticationPrincipal Member p) {
    return db.rows(
        "SELECT id,event_id,message,created_at,read_at FROM inbox WHERE workspace_id=? AND"
            + " recipient_id=? ORDER BY created_at DESC LIMIT 100",
        p.workspaceId(),
        p.userId());
  }

  @PostMapping("/{id}/read")
  public Object read(
      @AuthenticationPrincipal Member p,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String k) {
    return bus.run(
        p,
        "INBOX_READ:" + id,
        k,
        Map.of("id", id.toString()),
        null,
        c -> {
          if (db.update(
                  "UPDATE inbox SET read_at=COALESCE(read_at,now()) WHERE workspace_id=? AND id=?"
                      + " AND recipient_id=?",
                  p.workspaceId(),
                  id,
                  p.userId())
              == 0) throw DomainError.missing("Notification");
          return Map.of("id", id.toString());
        });
  }
}
