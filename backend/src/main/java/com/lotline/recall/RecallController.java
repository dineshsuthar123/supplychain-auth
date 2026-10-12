package com.lotline.recall;

import com.lotline.identity.Member;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/recalls")
public class RecallController {
  private final RecallService s;

  public RecallController(RecallService s) {
    this.s = s;
  }

  @GetMapping
  public Object list(@AuthenticationPrincipal Member p) {
    return s.list(p);
  }

  @PostMapping("/preview")
  public Object preview(
      @AuthenticationPrincipal Member p, @Valid @RequestBody RecallService.Preview r) {
    return s.preview(p, r);
  }

  @PostMapping
  public Object activate(
      @AuthenticationPrincipal Member p,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody RecallService.Activate r) {
    return s.activate(p, k, r);
  }

  @GetMapping("/{id}")
  public Object get(@AuthenticationPrincipal Member p, @PathVariable UUID id) {
    return s.detail(p, id);
  }

  @PostMapping("/{id}/acknowledge")
  public Object ack(
      @AuthenticationPrincipal Member p,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody RecallService.Ack r) {
    return s.acknowledge(p, id, k, r);
  }

  @PostMapping("/{id}/close")
  public Object close(
      @AuthenticationPrincipal Member p,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody RecallService.Decision r) {
    return s.close(p, id, k, r);
  }

  @PostMapping("/{id}/reopen")
  public Object reopen(
      @AuthenticationPrincipal Member p,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody RecallService.Decision r) {
    return s.reopen(p, id, k, r);
  }
}
