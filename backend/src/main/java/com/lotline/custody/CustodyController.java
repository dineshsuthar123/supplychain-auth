package com.lotline.custody;

import com.lotline.identity.Member;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers")
public class CustodyController {
  private final CustodyService s;

  public CustodyController(CustodyService s) {
    this.s = s;
  }

  @GetMapping
  public Object list(@AuthenticationPrincipal Member p) {
    return s.list(p);
  }

  @GetMapping("/{id}")
  public Object detail(@AuthenticationPrincipal Member p, @PathVariable UUID id) {
    return s.detail(p, id);
  }

  @PostMapping
  public Object draft(
      @AuthenticationPrincipal Member p,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody CustodyService.Draft r) {
    return s.draft(p, k, r);
  }

  @PostMapping("/{id}/dispatch")
  public Object dispatch(
      @AuthenticationPrincipal Member p,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody CustodyService.Dispatch r) {
    return s.dispatch(p, id, k, r);
  }

  @PostMapping("/{id}/receive")
  public Object receive(
      @AuthenticationPrincipal Member p,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody CustodyService.Receipt r) {
    return s.receive(p, id, k, r);
  }

  @PostMapping("/{id}/resolve")
  public Object resolve(
      @AuthenticationPrincipal Member p,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody CustodyService.Resolve r) {
    return s.resolve(p, id, k, r);
  }

  @PostMapping("/{id}/cancel")
  public Object cancel(
      @AuthenticationPrincipal Member p,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody CustodyService.Cancel r) {
    return s.cancel(p, id, k, r);
  }
}
