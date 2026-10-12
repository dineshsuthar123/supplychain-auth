package com.lotline.workspace;

import com.lotline.identity.Member;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspace")
public class WorkspaceController {
  private final WorkspaceService service;

  public WorkspaceController(WorkspaceService s) {
    service = s;
  }

  @GetMapping
  public Object get(
      @org.springframework.security.core.annotation.AuthenticationPrincipal Member p) {
    return service.get(p);
  }

  @PostMapping("/organizations")
  public Object org(
      @org.springframework.security.core.annotation.AuthenticationPrincipal Member p,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody WorkspaceService.Organization r) {
    return service.organization(p, k, r);
  }

  @PostMapping("/sites")
  public Object site(
      @org.springframework.security.core.annotation.AuthenticationPrincipal Member p,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody WorkspaceService.Site r) {
    return service.site(p, k, r);
  }

  @PostMapping("/invitations")
  public Object invite(
      @org.springframework.security.core.annotation.AuthenticationPrincipal Member p,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody WorkspaceService.Invite r) {
    return service.invite(p, k, r);
  }

  @PostMapping("/permissions")
  public Object permissions(
      @org.springframework.security.core.annotation.AuthenticationPrincipal Member p,
      @RequestHeader("Idempotency-Key") String k,
      @Valid @RequestBody WorkspaceService.Permissions r) {
    return service.permissions(p, k, r);
  }
}
