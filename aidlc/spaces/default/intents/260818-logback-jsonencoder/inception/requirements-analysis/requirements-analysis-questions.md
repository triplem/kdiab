# Requirements Analysis — Questions

Answer inline with `[Answer]:` tags. A–E preset options + X (Other).

## Q1 — Log-schema acceptance (blocks design & determines whether jackson can actually be shed)

Logback's native `JsonEncoder` emits a **fixed** schema: **epoch-millis** timestamp (not ISO-8601,
not configurable) and field keys `formattedMessage` / `loggerName` / `threadName` (not renamable).
This conflicts with `.claude/rules/logging.md` (mandates ISO-8601 UTC `timestamp` + `message` /
`logger` / `thread` names) and the Loki pipeline (#1023) may parse those. The primary goal —
removing jackson — is only reachable if this schema is accepted.

- **A.** Accept the native `JsonEncoder` schema; update `logging.md` and the Loki pipeline (#1023)
  parsers/labels to the new field names + epoch-millis timestamp. **Sheds jackson fully.** *(the
  issue's preferred direction; recommended)*
- **B.** Treat ISO-8601 + exact field names as a hard requirement → fall back to
  `logstash-logback-encoder`. **Keeps jackson** (only drops `logback-contrib`); the CVE force-pin
  stays. Partial win.
- **C.** Keep the status quo (no change); close #1556 as won't-do.
- **D.** Accept the native schema but keep `logging.md` as an aspirational target and add a
  follow-up issue to add a custom encoder later.
- **E.** —
- **X.** Other (specify)

[Answer]: A — Accept the native `JsonEncoder` schema; update `logging.md` and the Loki pipeline (#1023) to the new field names + epoch-millis timestamp. Shed jackson fully. (confirmed 2026-08-18)

## Q2 — Correlation-ID verification depth

`Correlation-ID` must remain visible in the JSON (`mdc`). How thorough should verification be?

- **A.** Boot each of the 8 services locally and grep stdout for the `mdc.Correlation-ID` field on a
  correlated request. *(recommended — highest confidence)*
- **B.** Verify on a representative subset (e.g. measures + analyze) and rely on the shared
  `kdiab-common` logging config for the rest.
- **C.** Trust the encoder's documented `mdc` behaviour + unit-level assertion; defer runtime check
  to CI/e2e.
- **X.** Other (specify)

[Answer]: C — Unit-level assertion on `mdc.Correlation-ID`; defer full runtime check to CI/e2e. (confirmed 2026-08-18)
