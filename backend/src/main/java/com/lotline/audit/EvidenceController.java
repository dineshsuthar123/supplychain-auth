package com.lotline.audit;

import com.lotline.identity.Member;
import com.lotline.shared.CommandBus;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/evidence")
public class EvidenceController {
  private final EvidenceService s;
  private final CommandBus bus;

  public EvidenceController(EvidenceService s, CommandBus bus) {
    this.s = s;
    this.bus = bus;
  }

  @GetMapping("/export")
  public ResponseEntity<byte[]> export(
      @AuthenticationPrincipal Member p, @RequestParam(required = false) Long sequence) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=lotline-evidence.zip")
        .contentType(MediaType.parseMediaType("application/zip"))
        .body(s.export(p, sequence));
  }

  @GetMapping("/projection-check")
  public Object compare(@AuthenticationPrincipal Member p) {
    return s.compareProjection(p);
  }

  @PostMapping("/rebuild")
  public Object rebuild(
      @AuthenticationPrincipal Member p, @RequestHeader("Idempotency-Key") String k) {
    return s.rebuild(p, k, bus);
  }
}
