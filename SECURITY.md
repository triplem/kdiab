# Security Policy

## Supported Versions

kdiab is a development/educational platform. Security fixes are applied to the `main` branch only.

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

Report vulnerabilities by email to: **markusmay@gmail.com**

Include as much of the following as possible:

- Type of issue (e.g. SQL injection, authentication bypass, sensitive data exposure)
- Full path of the source file(s) related to the issue
- Step-by-step instructions to reproduce
- Proof-of-concept or exploit code (if possible)
- Impact assessment — what an attacker could do with this issue

## Response Timeline

| Step | Target |
|---|---|
| Acknowledgement | Within 72 hours |
| Initial assessment | Within 7 days |
| Fix or mitigation | Within 30 days for critical/high issues |

## Scope

**In scope:**

- Authentication and authorisation bypass (JWT validation, role enforcement, patient access control)
- SQL injection or data leakage between patient records
- Secrets exposed in Docker images, logs, or API responses
- Keycloak misconfiguration enabling privilege escalation

**Out of scope:**

- Vulnerabilities in third-party dependencies with no exploit path in this application
- Denial-of-service attacks requiring physical access or excessive resources
- Issues already reported or in the process of being fixed

## Preferred Language

English or German.
