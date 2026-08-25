# Scalability Requirements — Guard test-mode JWT out of production

> Scope: security-patch. A startup config guard has **no scaling dimension**.

## Requirements

| ID | Requirement | Target |
|---|---|---|
| **SC-1** | The guard is stateless and per-process; it introduces no shared state, no coordination, and no new external dependency. | No impact on horizontal scaling. |
| **SC-2** | Behaviour is identical across every replica of every service (logic lives once in `kdiab-common`). | Uniform across all 8 backends and all replicas. |

## Rationale

The change does not touch data stores, connection pools, or request routing. The pre-existing
PostgreSQL horizontal-scaling considerations (tracked separately under issue #1140) are unaffected.
