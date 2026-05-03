# kdiab-profiles

Insulin pump basal profile management service.

## What this service does

kdiab-profiles manages basal rate profiles for insulin pump users. A profile is a 24-hour schedule of basal rates (units/hour) along with insulin-to-carb ratios (ICR) and insulin sensitivity factors (ISF).

Profiles follow a copy-on-write state machine:

```
DRAFT → PROPOSED → ACTIVE → ARCHIVED
```

Only one profile can be ACTIVE per user at a time. Activating a new profile automatically archives the previous one. Doctors can propose profiles for their assigned patients; patients approve or reject proposals. Active profiles are immutable — any update archives the current and creates a new ACTIVE version with a new ID.

## PostgreSQL requirement

The service enforces a partial unique index that allows only one active profile per user:

```sql
CREATE UNIQUE INDEX IDX_PROFILES_USER_ACTIVE
  ON profiles(user_id)
  WHERE status = 'ACTIVE';
```

This index uses PostgreSQL-specific `WHERE` clause syntax and is **not** applied to H2 (used in integration tests). **PostgreSQL is required for any environment where the uniqueness constraint must be enforced.** The root `docker-compose.yml` uses PostgreSQL automatically.

## Build, run, and test

See the [root README](../README.md) for prerequisites, build commands, service URLs, and test accounts.
