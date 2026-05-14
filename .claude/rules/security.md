# Rule: Security

## OWASP Top 10 Checklist

Every story touching user input, auth, or data access must be reviewed against:

### A01 Broken Access Control

- Verify authorisation on every endpoint (not just authentication).
- Use attribute-based or role-based access control — never manual `if (user.role == "admin")` checks scattered across the code.
- Deny by default: endpoints are private unless explicitly made public.

```kotlin
// Spring Security — explicitly declare public endpoints
http.authorizeHttpRequests {
    it.requestMatchers("/api/v1/health", "/api/v1/auth/login").permitAll()
    it.anyRequest().authenticated()
}
```

### A02 Cryptographic Failures

- Never store passwords in plain text or reversible encryption — use bcrypt/argon2.
- TLS everywhere — no HTTP for sensitive data.
- No secrets in environment variables committed to source — use secrets manager (Vault, AWS Secrets Manager).
- JWTs: sign with RS256 (asymmetric), not HS256 in multi-service environments.

### A03 Injection

- Use parameterised queries — never string concatenation in SQL.
- Validate and sanitise all user inputs at the boundary.
- Use ORM (JPA, EF Core, Prisma) where possible.

```kotlin
// BAD
val query = "SELECT * FROM users WHERE email = '$email'"

// GOOD
val user = userRepository.findByEmail(email)  // Spring Data parameterises automatically
```

### A05 Security Misconfiguration

- Remove default credentials, sample data, and debug endpoints before deploy.
- Disable stack traces in API responses (return generic error messages in production).
- Set security headers: `Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options`.

### A07 Identification & Authentication

- Enforce strong password policy or use passkeys/SSO.
- Implement account lockout after N failed attempts.
- Invalidate sessions on logout.
- JWT expiry ≤ 15 minutes for access tokens; refresh tokens are rotated.

### A09 Security Logging & Monitoring

- Log all auth events: login success, login failure, token refresh, password change.
- Log access to sensitive data (PII access).
- Never log credentials, tokens, or raw PII in log messages.

## Secret Hygiene

- Secrets never appear in source code, commits, or logs.
- Use `.env` files locally (gitignored) and secrets manager in production.
- Pre-commit hook runs `gitleaks detect` or `detect-secrets scan`.

```bash
# .husky/pre-commit
gitleaks protect --staged --redact
```

## Dependency Security

```bash
# Weekly audit (or on every PR)
npm audit --audit-level=high
./gradlew dependencyCheckAnalyze  # OWASP Dependency Check
dotnet list package --vulnerable
```

Treat HIGH/CRITICAL CVEs as bugs — fix within 1 sprint.

## SAST

Semgrep runs on every PR:

```bash
semgrep --config=auto --error --severity=ERROR src/
```

Language-specific additions:
- **Kotlin/Java**: SpotBugs + Find Security Bugs plugin
- **TypeScript**: `eslint-plugin-security`
- **.NET**: `Security Code Scan`

## Container Security

```dockerfile
# Use non-root user
RUN addgroup --system app && adduser --system --group app
USER app

# Pin base image digest
FROM eclipse-temurin:21-jre@sha256:abc123...
```

Run `trivy image ${IMAGE}` as part of CI.
