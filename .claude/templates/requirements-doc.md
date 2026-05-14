# Requirements Document

**Project:** {{PROJECT_NAME}}
**Version:** 1.0
**Status:** DRAFT | APPROVED
**Date:** {{DATE}}
**Approved by:** {{APPROVER}}

---

## 1. Executive Summary

{{1–3 paragraph description of the project goal, the problem it solves, and who benefits.}}

---

## 2. Stakeholders

| Role | Name / Team | Responsibility |
|---|---|---|
| Product Owner | | Final sign-off on scope |
| Technical Lead | | Architecture decisions |
| End Users | | Primary system users |
| Operations | | Deployment & support |

---

## 3. Functional Requirements

### 3.1 {{Domain Area 1}}

| ID | Requirement | Priority | Acceptance Test Sketch |
|---|---|---|---|
| FR-001 | {{Requirement description}} | Must Have | {{How we'd verify this}} |
| FR-002 | | Should Have | |
| FR-003 | | Could Have | |

### 3.2 {{Domain Area 2}}

| ID | Requirement | Priority | Acceptance Test Sketch |
|---|---|---|---|
| FR-010 | | Must Have | |

---

## 4. Non-Functional Requirements

| ID | Category | Requirement | Measurement |
|---|---|---|---|
| NFR-001 | Performance | API response time p95 < 500ms | Load test with k6 |
| NFR-002 | Availability | 99.9% uptime SLA | Uptime monitoring |
| NFR-003 | Security | OWASP Top 10 compliant | SAST + penetration test |
| NFR-004 | Scalability | Support 10,000 concurrent users | Load test |
| NFR-005 | Compliance | GDPR — right to erasure | Data deletion test |
| NFR-006 | Observability | All critical operations traced | Trace coverage audit |

---

## 5. System Context

### 5.1 Integration Points

| System | Direction | Protocol | Purpose |
|---|---|---|---|
| {{External System}} | Inbound | REST/HTTPS | {{Purpose}} |
| {{External System}} | Outbound | REST/HTTPS | {{Purpose}} |

### 5.2 Context Diagram

```
[User] → [{{System}}] → [Database]
                      → [External API]
                      ← [Message Queue]
```

---

## 6. Domain Model (Sketch)

```
{{Entity}} 1──* {{Entity}}
{{Entity}} *──* {{Entity}}
```

Key entities:
- **{{Entity}}**: {{Description}}

---

## 7. Assumptions

| ID | Assumption | Risk if Wrong |
|---|---|---|
| A-001 | {{Assumption}} | {{Risk}} |

---

## 8. Constraints

| ID | Constraint | Source |
|---|---|---|
| C-001 | Must run on AWS | Infrastructure decision |
| C-002 | Team has no .NET experience | Skills constraint |
| C-003 | Go-live by {{DATE}} | Business deadline |

---

## 9. Out of Scope (v1)

- {{Feature explicitly excluded}}
- {{Feature deferred to v2}}

---

## 10. Open Questions

| ID | Question | Owner | Due |
|---|---|---|---|
| Q-001 | {{Question}} | {{Name}} | {{Date}} |

---

## 11. Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R-001 | {{Risk}} | High | High | {{Mitigation}} |

---

## Approval

- [ ] Product Owner: {{Name}} — Date: ___
- [ ] Technical Lead: {{Name}} — Date: ___
