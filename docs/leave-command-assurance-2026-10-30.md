# Leave command and delegation completion — 30 October 2026

The review found twenty-five failing cases beyond the earlier ten reader failures.
Older policy/accrual/cancellation commands could complete after MFA expiry; policy
contract sets could change while awaiting acquisition. Submission, withdrawal and
cancellation receipt paths could bypass current request-management scope. Temporary
delegation could expire before a read, displayed action or decision completed.
Decision replay also bypassed current delegation and beneficiary exclusions.

Nine interactive use cases now retain their authority through the relevant final
boundary. Policy inputs own their contract set before validation/hashing. Receipt
paths check current resource scope. Request and attachment readers acquire the
actor and relevant delegator accounts in sorted order, reacquire identity/grants,
and check delegation at completion. Ordinary read scope remains independent;
available decision actions are refreshed while keeping payroll/year-close gates.

Decisions evaluate the current stage after acquisition and again after journaling
using the original locked approval snapshot. A later stage cannot authorize the
transaction retrospectively. Receipt replay selects the original pre-decision
history revision with one page of one item (at most two raw rows), so later
cancellation workflows cannot replace the relevant approval. Current assignment,
beneficiary exclusions and session assurance still apply. A newly bound beneficiary
cannot replay an earlier independent decision.

No schema, transport, constructor or DI dependency changed. Read transactions add
one identity acquisition; submission/withdrawal/cancellation receipts add one
request acquisition. Decision receipts add bounded history, beneficiary,
delegation and grant acquisitions; new decisions add no queries. Storage remains
outside transactions with the bounded buffer ownership accepted in the earlier fix.

The first 22-case baseline had 19 actual failures and three correct ordinary-read
controls (68s). Six receipt cases then had four actual failures and two correct
original-workflow controls (41s). Two submission/withdrawal receipt cases both
failed (37s). No compilation/setup failure is included in these counts. Four
additional account-wait regressions verify current grants after delegator
revocation, denied storage preparation and recovery after restoration.

Focused corrections passed 32 then 42 API cases with 65 domain cases. The broad
acceptance passed 471 API/OpenAPI cases and quality/packages in 857s.
Review of the unpublished correction then added eight capacity compatibility
regressions: a 201-row delegation sentinel must preserve ordinary/direct read
scope and direct original receipts. Four failures reproduced the compatibility
issue; four initial fixture failures lacked the administrator grant for reassignment
and are excluded. Their fixture now uses the administrator. Six other controls
already passed. The initial correction run failed only those four fixture setups
(118s); final evidence below supersedes it. Account guard selection stays bounded and never grants scope from a truncated delegation
set; new decisions retain the original capacity rejection. The final targeted
acceptance passed 58 API cases plus 65 domain cases and quality/packages
in 115s. The two selections cover 485 distinct API/OpenAPI cases,
without failures, errors or skipped cases. The separate Leave data task has no test sources; PostgreSQL API/mapper
suites and retained migration tests cover its storage boundaries. Formatting,
architecture and both application packages passed in both acceptance selections.

Six actual API/PostgreSQL/browser workflows passed in 50.4s with setup. Three
API/PostgreSQL/worker/browser workflows passed in 53.4s with setup: accrual and
year closing, finite schedules with preserved future recovery time, and carry
expiry with exactly one debit. [Measurements](measurements/leave-command-assurance-2026-10-30.json)
retain exact suites, scenarios and package hashes. The [requirement review](leave-feature-review-2026-10-30.md)
covers all five initial rows and eleven original detailed sections. Final-source
CI and the remaining Platform/Mobile audit stay separately tracked.
