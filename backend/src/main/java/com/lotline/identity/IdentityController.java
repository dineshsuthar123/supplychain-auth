package com.lotline.identity;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class IdentityController {
  private final IdentityService identity;
  private final boolean secure;

  public IdentityController(
      IdentityService identity, @Value("${lotline.secure-cookies}") boolean secure) {
    this.identity = identity;
    this.secure = secure;
  }

  private String cookieName() {
    return secure ? "__Host-lotline-refresh" : "lotline-refresh";
  }

  private ResponseCookie cookie(String value, boolean clear) {
    return ResponseCookie.from(cookieName(), value)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Strict")
        .path("/")
        .maxAge(clear ? Duration.ZERO : Duration.ofDays(14))
        .build();
  }

  private ResponseEntity<?> issued(IdentityService.Issued s) {
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie(s.refresh(), false).toString())
        .body(s.body());
  }

  private String raw(HttpServletRequest r) {
    if (r.getCookies() != null)
      for (Cookie c : r.getCookies()) if (cookieName().equals(c.getName())) return c.getValue();
    return null;
  }

  @PostMapping("/register")
  public ResponseEntity<?> register(
      @Valid @RequestBody IdentityService.Register r,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return issued(identity.register(r, key));
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@Valid @RequestBody IdentityService.Login r) {
    return issued(identity.login(r));
  }

  @PostMapping("/accept-invitation")
  public ResponseEntity<?> accept(@Valid @RequestBody IdentityService.Accept r) {
    return issued(identity.accept(r));
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(HttpServletRequest r) {
    return issued(identity.refresh(raw(r)));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(HttpServletRequest r) {
    identity.logout(raw(r));
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie("", true).toString())
        .build();
  }

  @GetMapping("/me")
  public IdentityService.Session me(
      @org.springframework.security.core.annotation.AuthenticationPrincipal Member p) {
    Member m = p;
    return identity.body(m.userId(), m.sessionId());
  }

  public record Switch(UUID workspaceId) {}

  @PostMapping("/workspace")
  public IdentityService.Session workspace(
      @org.springframework.security.core.annotation.AuthenticationPrincipal Member p,
      @RequestBody Switch r) {
    return identity.switchWorkspace(p, r.workspaceId());
  }
}
