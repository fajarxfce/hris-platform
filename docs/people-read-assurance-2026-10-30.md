# People read completion — 30 October 2026

Twenty-eight actual reproductions showed older readers releasing data after MFA
expired during acquisition. Twenty-four use cases now retain the guarded identity
snapshot and validate current assurance after their final data acquisition/mapping.
The existing original/live permission intersection remains the scope boundary.

The correction covers employee directories/details and SELF discovery, employment
references/history/revisions/transfers, private profiles/history, company directory
and department choices, lifecycle templates/cases/history/assignments, offboarding
review, and import summaries/rows/attempts. Transfer review validates each acquired
identity at the current clock and rechecks both retained company scopes before
returning data. Errors retain the affected company parameter. Its original/live
destination grant capture remains intact.

No schema, constructor, DI dependency or query was added. The existing guarded
AccountAccess snapshots remain stable under the transaction locks. Page bounds,
private/public projections and independent worker ownership remain intact. Client
availability is an admission policy: already admitted work may finish. Review dates
still share the existing evaluation instant; the correction does not add policy
queries or reinterpret maintenance as authorization expiry.

The 26-case reader baseline failed in 55s; the separate two-case transfer-review
baseline failed in 34s. An earlier SELF fixture lacked its explicit read grant
(three setup failures, excluded), and an initial import wrapper compilation used
the wrong repository signature (no tests executed). Both were corrected before
counting reproductions. Tests exercise real PostgreSQL acquisitions, original
admission, late expiry, unchanged journal/receipt state and successful renewal.

The first focused correction passed 26 API, 50 domain and fourteen repository
cases in 69s. Final broad acceptance, including transfer review, passed 427
API/OpenAPI, 50 domain and fourteen repository cases with zero failures, errors or
skips. Formatting, architecture and both packages passed in 1124s. Three
actual API/browser workflows passed in 36.5s with setup (employee imports,
transfer and person/account binding); two actual API/worker/browser workflows
passed in 37.3s (scheduled onboarding and offboarding). [Measurements](measurements/people-read-assurance-2026-10-30.json)
retain exact suites, scenarios, setup exclusions and package hashes.

All five People initial rows remain unverified until the command/input and
Organization review is complete. Older command journal/replay completion and the
owned lifecycle task-list/assignee-map inputs require separate reproduction;
Organization's older company/unit readers and commands also remain in scope.
The existing schedule/projection completion checks retain separate evidence.
This correction and its green checks do not close the full feature goal.
