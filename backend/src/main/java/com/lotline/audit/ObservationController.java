package com.lotline.audit;

import com.lotline.identity.Member;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/observations")
public class ObservationController {
  private final ObservationService s;

  public ObservationController(ObservationService s) {
    this.s = s;
  }

  @GetMapping
  public Object list(@AuthenticationPrincipal Member p) {
    return s.list(p);
  }

  @PostMapping
  public Object report(
      @AuthenticationPrincipal Member p,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody ObservationService.Observation r) {
    return s.report(p, k, r);
  }

  @PostMapping("/{id}/resolve")
  public Object resolve(
      @AuthenticationPrincipal Member p,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody ObservationService.Resolution r) {
    return s.resolve(p, id, k, r);
  }
}
