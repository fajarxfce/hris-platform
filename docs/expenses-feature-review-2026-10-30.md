# Expenses requirement review — 30 October 2026

The six initial requirements and nine original detailed sections have been traced
to source and matching behavior. The [assurance/input correction](expense-assurance-2026-10-30.md)
closes 29 reproduced failures. Broad and final decision runs passed 335 distinct
API cases, 20 Expense domain cases and 115 Reporting cases, with quality/packages.
Four current API/browser workflows passed in 1.2 minutes including setup. All six
rows are locally accepted; final-source CI remains separately tracked.

| Requirement | Implementation and matching behavior |
| --- | --- |
| `expenses.01`: categories, claim evidence, review and payments | Effective category policy, immutable draft/submission/review repositories and separate finance batches. Policy/draft/submission/review/payment API suites and actual category, claim and payment browser workflows. |
| `expenses.02`: IDR, dates, cost centers, frozen limits, duplicate signals and correction | Decimal line/category-total policies; submission captures effective policy, employee, cost-center and accepted receipt evidence. Duplicate digests are historical submission signals and recomputed decision inputs. Review tests cover acknowledge/reject/return, revised resubmission, makers and beneficiaries. |
| `expenses.03`: stable payment references, item outcomes and paid-claim exclusion | Immutable instructions, company-unique bank references and a partial unique reservation for prepared/pending/successful items. Independent preparation/release/reconciliation, explicit no-transfer failures and optimistic versions. Actual payment tests cover partial outcomes, lost replies, failed-item replacement and unchanged approved evidence. External bank execution remains manual by contract. |
| `expenses.04`: claim, review, policy and finance screens | Dedicated dashboard controllers and pages for drafting, policy review, submission decisions, payable selection, full destination review, release/cancel/export and reconciliation. The unchanged dashboard tree has 98 matching Chromium outcomes in accepted source `8014d733`, including four actual API/browser workflows. The four current API workflows also pass after correction. |
| `expenses.05`: bounded coherent claim review, immutable submissions, receipt access and authoring | Separate current-claim aggregate and exact-submission endpoints, scope-aware receipt downloads, versioned decisions and retained return context. Claim review/record, submission actions, drafting references, scope, receipt and browser tests exercise authoring, returned correction, independent review and account/company lifecycle. |
| `expenses.06`: limits, company attachments, concurrency, replay and immutable settlement | Category limits across effective revisions, immutable document references, submission/withdrawal serialization, versioned payment reservations/results and deferred database consistency constraints. API bounds/security/replay suites plus the current acquisition, journal, input-ownership and delegation corrections. |

Detailed source review covers all nine sections:

- Operational expense reporting selects the latest immutable submitted lines by
  transaction date, keeps current status distinct from frozen content and payment,
  excludes unsubmitted drafts and rechecks company-wide report/expense authority.
- Category heads remain separate from the effective revision. Limits are 500
  categories/1,000 revisions; inactive revisions archive instead of rewriting.
  Save inputs, history, versions, audit and operation receipts remain coherent.
- Employee discovery and editor references use expense admission independently
  of People browsing. `SELF` narrows even administrative callers; canonical claim
  and payment progress use separate owned synchronization versions.
- Drafts retain at most twenty lines/three receipt references per line, with
  1,000 company/50 creator open claims and 100 revisions. Historical evidence
  references remain after later removal or cancellation. Commands detach inputs.
- Administrative and owned reference lookups return bounded minimal metadata,
  never imply content access and never reserve submission eligibility.
- Submission freezes transaction-date eligibility, decimal totals, effective
  category versions, cost-center labels and validated receipt manifests. Each
  category must select one workflow using the whole amount. Candidate overflow
  fails before maker filtering; missing independent reviewers produce a blocked
  stage. Twenty submissions remain recoverable by withdrawal/cancellation.
- Decisions retain original/current beneficiaries and every maker as excluded
  actors, including delegation sources. Both observed versions matter. Return
  requires a new draft before resubmission; paid evidence is never rewritten.
  Current-stage delegation is revalidated at decision time and after journaling;
  six actual late-expiry cases verify rollback and original-command recovery.
- Receipt content belongs to the exact immutable submission, with short guarded
  transactions around external I/O, Range/ETag/HEAD, no-store/nosniff and cleared
  owned buffers. The current correction retains delegation and ordinary authority
  through metadata and byte delivery.
- Finance has a separate protected grant, recent authentication, at most 100
  items, bounded dates/pages, 100 open/10,000 total batches and twenty attempts
  per claim. CSV references are deterministic; partial reconciliation disables
  export. Unknown transfer outcomes stay pending. The dashboard retains one
  reviewed command for explicit response-loss recovery and cancels obsolete work.

Existing source and downloaded CI reports support unchanged components; current
correction acceptance is separately retained in the [measurement](measurements/expenses-feature-review-2026-10-30.json). No live transfer,
mail, private-runtime update, phone interaction or generic Starter change occurred.

Exact source `5b60aba3` completed [CI 41018107365](https://github.com/fajarxfce/hris-platform/actions/runs/41018107365)
with nine jobs passing. Normal API/browser integration and all fourteen worker
scenarios passed, but the worker step took 5.2 minutes against its five-minute
budget. The job failed on that timeout and skipped subsequent storage/export
checks. The revised workflow allows eight minutes for this step and twenty for
the integration job, keeping per-test limits and zero retries. [Evidence](measurements/ci-integration-timeout-2026-10-30.json)
retains this failure; complete final-source CI remains pending.
