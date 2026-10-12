package com.lotline.lineage;

import com.lotline.identity.Member;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class TraceController {
  private final TraceService trace;

  public TraceController(TraceService t) {
    trace = t;
  }

  @GetMapping("/trace")
  public Object graph(
      @AuthenticationPrincipal Member p, @RequestParam(required = false) Long sequence) {
    return trace.graph(p, sequence);
  }

  @GetMapping("/inventory/holdings")
  public Object holdings(
      @AuthenticationPrincipal Member p, @RequestParam(required = false) Long sequence) {
    return trace.graph(p, sequence).get("holdings");
  }

  @GetMapping("/events")
  public Object events(
      @AuthenticationPrincipal Member p,
      @RequestParam(defaultValue = "0") long after,
      @RequestParam(defaultValue = "100") int limit,
      @RequestParam(required = false) Long sequence) {
    long seq = trace.sequence(p, sequence);
    var rows = trace.events(p, seq, after, limit);
    return Map.of(
        "sequence",
        seq,
        "events",
        rows,
        "nextCursor",
        rows.isEmpty() ? after : rows.getLast().get("sequence"),
        "hasMore",
        rows.size() == Math.min(limit, 200));
  }

  @GetMapping("/health")
  public Object health() {
    return Map.of("status", "UP", "product", "Lotline");
  }
}
