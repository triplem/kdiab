# Performance Test Instructions — logback-jsonencoder

## Not a performance-sensitive change (no dedicated perf test required)

Swapping the Logback JSON encoder does not alter request handling, business logic, or I/O paths — it
changes only how each already-emitted log event is serialized. The native `JsonEncoder` is a
first-party encoder aligned with the in-use Logback 1.5.32 and is expected to be at least as fast as
the `logback-contrib` `JacksonJsonFormatter` (one fewer indirection layer; no jackson).

## Light confidence checks (optional)

- Confirm logging is asynchronous/normal as before — the appender (`ConsoleAppender` → `STDOUT`) is
  unchanged; only its encoder changed.
- If a perf baseline exists (issue #1584 tracks the absence of a perf-test tier), no regression is
  expected; re-baselining is out of scope for #1556.

The team's Minimal test-strategy for `refactor` scope does not mandate a new performance test here.
