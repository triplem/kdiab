# Accepted Security Risks

This document records security findings that have been reviewed and accepted as non-critical
risks, along with justification and scheduled review dates.

---

## AR-001 — libxml2 Denial of Service in kdiab-ui Docker image

| Field | Value |
|---|---|
| **CVE** | CVE-2024-25062 / CVE-2026-6732 (libxml2 DoS via crafted XSD-validated document) |
| **Severity** | MEDIUM–HIGH (CodeQL alert #8) |
| **Component** | `nginx:alpine` base image in `kdiab-ui/Dockerfile` |
| **Reported** | 2026-06-03 |
| **Updated** | 2026-06-04 (Dockerfile digest updated; CVE-2026-6732 added) |
| **Review date** | 2026-12-03 |

### Finding

The `nginx:alpine` base image ships with a version of `libxml2` that has a known
Denial-of-Service vulnerability. An attacker can trigger the DoS by sending a crafted
document that causes libxml2 to enter an infinite loop during XSD schema validation.

Two CVEs cover this vulnerability family in libxml2:
- **CVE-2024-25062** (MEDIUM) — original report
- **CVE-2026-6732** (HIGH) — additional CVE, libxml2 2.13.9-r0; fixed in 2.13.9-r1

The `nginx:alpine` base image digest was updated on 2026-06-04 to obtain libxml2 2.13.9-r1.
If Trivy still reports this finding after the digest update, the new image may still include
the vulnerable package — follow the remediation path below.

### Justification for acceptance

`kdiab-ui` uses nginx exclusively as a **static file server and reverse proxy** for a
React SPA. The `nginx.conf` configures no XSLT filter (`ngx_http_xslt_filter_module`),
no WebDAV, and no XML processing of any kind. All request paths either serve pre-built
files from `/usr/share/nginx/html` or proxy HTTP API calls to backend services.

libxml2 is linked into nginx for the XSLT and XML processing modules, which are compiled
in by the Alpine nginx package but **never activated by the kdiab-ui configuration**. For
the XSD validation path to be triggered, nginx would need to parse an attacker-supplied
XML document via an XSLT transformation — which requires an explicit `xslt_stylesheet`
directive that is not present. The vulnerability is therefore not reachable from
`kdiab-ui`'s attack surface.

### Conditions for re-evaluation

- If `nginx.conf` is ever extended with XSLT/XML processing directives
- If a patched `nginx:alpine` image becomes available (check on each review date)
- If the vulnerability severity is upgraded to CRITICAL

### Remediation path

If a patched `nginx:alpine` image is available:
1. Run `docker pull nginx:alpine` to get the latest image and its digest
2. Update `kdiab-ui/Dockerfile` — replace the `FROM nginx:alpine@sha256:...` digest
3. Run `trivy image` to confirm the finding is resolved
4. Open a PR referencing this entry and remove it once the new image is verified clean

### Dismissal

CodeQL alert #8 should be dismissed as **"Risk accepted"** with the note:
"libxml2 is not reachable from kdiab-ui — nginx configured as static file server only,
no XSLT processing. See docs/security/accepted-risks.md AR-001."
