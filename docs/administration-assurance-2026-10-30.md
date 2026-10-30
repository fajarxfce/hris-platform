# Administration completion assurance

The Administration review found private settings and audit reads that could return
data after the caller's MFA proof expired during acquisition. Named policy history
also omitted the use-case enrollment/proof check. Policy writes could commit after
recent authentication expired during journaling, and accepted a recent MFA timestamp
without checking current enrollment. The existing company, membership, account and
policy guards remain responsible for concurrent access changes.

`GetCompanyClientPolicySettings`, `GetCompanyClientPolicyRevision` and
`SearchAuditEvents` now check assurance before releasing acquired values. Named
history also checks before acquiring private revisions. Settings retain their
original effective-policy evaluation time; audit retains its original query window
and observation time. No extra database query is introduced.

`SaveCompanyClientPolicy` validates current enrollment/general assurance and the
applicable recent password/MFA proof after its guards, before receipt replay, and
after journaling. A late failure returns through `TransactionRunner`, rolling back
the policy head, immutable revision, receipt, audit and event. An explicit retry
with renewed proof and the original operation succeeds once. Stable failure codes
remain `mfa_setup_required`, `mfa_required` and `recent_authentication_required`.

The shared Administration configuration supplies the added security policy and
clock to named history in both application graphs. API and worker runtime tests
are required; packaging alone is not dependency-injection verification. No schema,
endpoint shape or client command changes are needed.

## Reproduction and compatibility

The final baselines contain eleven genuine failed cases and one already-correct
control. Eight reader cases cover latest/effective settings, latest/named history,
audit page/cursor acquisition, and enrollment/proof denial before named-history
acquisition. Three command cases cover late password/MFA expiry and missing current
enrollment. The ordinary write with enforcement disabled already passed.

Two initial cursor fixtures used the application test clock before the database's
real audit timestamps, producing an empty seed page. Those setup failures are
excluded. The corrected baseline advances only application time to a bounded
instant after seeding; all eight reader failures then reproduce the actual defect.
Initial expected error names were corrected to the existing public codes. An
incorrect `spotlessApply` invocation ran no checks; the repository's `format` task
subsequently passed. The measurement preserves these distinctions.

Public company client-policy discovery and business availability admission retain
their explicit recovery contract. They do not disclose private future settings or
editor reasons, and do not acquire the new private-settings MFA requirement.
Availability changes do not abort already-admitted transactions/jobs or grant
business permissions. `ClientPolicy` already snapshots its disabled-module set.

Global integration configuration, worker observations, platform audit/account
history and job list/detail/cancellation already check final assurance. Their
existing regression suites remain in the broader verification selection. The
one-hour elapsed export-retention worker case keeps its earlier independent
acceptance and is explicitly skipped in the ordinary worker run.

## Verification

The focused run passed all twelve new API cases, eight Administration domain and
fourteen repository cases, architecture and formatting in 53.571 seconds. Broader
verification passed 81 API cases, 104 active worker cases, eight Administration
domain, fourteen repository and two Jobs domain cases, architecture/format checks
and both application packages in 242.548 seconds.

Actual API/browser and API/worker/browser results are recorded in
[measurements](measurements/administration-assurance-2026-10-30.json). The
[module review](administration-feature-review-2026-10-30.md) maps the five initial
requirements and six detailed sections. Full source CI remains a separate gate.
The private stack currently serves the previously accepted metadata packages at
schema 103; this source correction has not yet been deployed.
