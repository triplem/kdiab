<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-08-15T19:39:32Z — Q2 (aiming for formal GDPR/medical-device compliance) + kdiab-calc elevated the compliance angle to a top finding: flagged EU MDR / Software-as-a-Medical-Device exposure and GDPR special-category (Art. 9) handling. This makes dose-calc correctness the highest-stakes thread (reinforces build-vs-buy.md).
- 2026-08-15T19:39:32Z — AWS-platform support perspective is N/A (self-hosted, no cloud/Bedrock by project rule); reduced it to "keep self-hosted; cloud-native ideas out of scope". Capacity=occasional bursts (Q1) → roadmap ranked by value-density / independently-shippable chunks, not calendar.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
