package com.lotline.inventory;

import com.lotline.identity.Member;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
  private final InventoryService service;

  public InventoryController(InventoryService s) {
    service = s;
  }

  @PostMapping("/commission")
  public Object commission(
      @AuthenticationPrincipal Member p,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody InventoryService.Commission r) {
    return service.commission(p, k, r);
  }

  @PostMapping("/operations")
  public Object operation(
      @AuthenticationPrincipal Member p,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody InventoryService.Operation r) {
    return service.operation(p, k, r);
  }

  @PostMapping("/dispositions")
  public Object disposition(
      @AuthenticationPrincipal Member p,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody InventoryService.Disposition r) {
    return service.disposition(p, k, r);
  }

  @PostMapping("/corrections")
  public Object correction(
      @AuthenticationPrincipal Member p,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody InventoryService.Correction r) {
    return service.correction(p, k, r);
  }
}
