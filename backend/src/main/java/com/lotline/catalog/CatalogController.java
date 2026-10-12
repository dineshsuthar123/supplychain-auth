package com.lotline.catalog;

import com.lotline.identity.Member;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class CatalogController {
  private final CatalogService service;

  public CatalogController(CatalogService s) {
    service = s;
  }

  @GetMapping
  public Object list(@AuthenticationPrincipal Member p) {
    return service.products(p);
  }

  @PostMapping
  public Object create(
      @AuthenticationPrincipal Member p,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody CatalogService.Product r) {
    return service.create(p, k, r);
  }
}
