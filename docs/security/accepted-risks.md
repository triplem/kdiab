# Accepted Security Risks

This file documents security findings that have been reviewed and accepted as acceptable risks.
Each entry must be reviewed and renewed by the date listed.

---

## CVE-2026-6732 — libxml2 Denial of Service (nginx base image)

| Field | Value |
|---|---|
| **CVE** | CVE-2026-6732 |
| **Package** | libxml2 2.13.9-r0 (Alpine OS package) |
| **Severity** | HIGH |
| **Affected image** | kdiab-ui Docker image (nginx:alpine base) |
| **Fixed in** | libxml2 2.13.9-r1 |
| **Date accepted** | 2026-06-04 |
| **Review date** | 2026-09-04 |
| **CodeQL alert** | #8 |

**Vulnerability**: Denial of Service via crafted XSD-validated document in libxml2.

**Justification**: libxml2 is an indirect OS-level dependency of nginx:alpine. The kdiab-ui
container runs nginx as a static file server — it does not parse XML or XSD documents from
user input. The vulnerability requires an attacker to supply a specially crafted XSD document
to a libxml2 API call, which nginx does not make in its HTTP serving path.
Risk of exploitation in kdiab-ui is negligible.

**Resolution**: The nginx:alpine base image digest was updated in `kdiab-ui/Dockerfile` on
2026-06-04. If the updated image still ships libxml2 2.13.9-r0, upgrade to the next nginx
patch release or rebuild the base layer with `apk add libxml2=2.13.9-r1`.

**Tracked in**: [Issue #1460](https://github.com/triplem/kdiab/issues/1460)
