# Security model

## Route policy

Public routes are restricted to registration/login/refresh, health, and local API documentation. Every `/api/**` route requires a validated access JWT. Enrollment requires `ROLE_MANUFACTURER` or `ROLE_ADMIN`; image verification requires verifier, manufacturer, or admin; evidence/dashboard reads allow auditor, verifier, manufacturer, or admin.

The access token carries the authenticated user ID, role, and tenant ID. `JwtAuthenticationFilter` validates token type and populates the Spring Security authority plus request-scoped `TenantContext`. The context is cleared at request completion.

## Credentials and sessions

Passwords use BCrypt. Login returns an access token and puts the rotating refresh token in a secure, HTTP-only, SameSite=Lax cookie. Refresh checks the persisted BCrypt refresh-token hash and expiry before issuing a replacement, so a previously rotated token cannot be reused.

## Browser and upload controls

`SUPPLYPRINT_CORS_ALLOWED_ORIGINS` controls allowed origins (default `http://localhost:3000`); wildcard credentials are not enabled. Image uploads are validated by magic bytes and decoder, accept only binary JPEG/PNG, require sane dimensions, and cap payloads at 10 MB. SVG/XML/polyglot inputs fail the binary-signature check.

## Rate limiting and limitations

`ApiRateLimitFilter` enforces a 120 request/minute per-IP/per-route fixed window by default. It is intentionally an in-process safety valve; production multi-instance deployments must move this counter to Redis, gateway, or a dedicated rate-limit service.

## Acceptance checks

```powershell
# Expected: 403
Invoke-WebRequest http://localhost:10000/api/dashboard
# Expected: 200
Invoke-WebRequest http://localhost:10000/actuator/health
```

Do not expose `/actuator/prometheus` publicly in production; it is admin-only in the application policy and should also be restricted at ingress.
