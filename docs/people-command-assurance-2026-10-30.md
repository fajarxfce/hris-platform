# People commands and Organization completion — 30 October 2026

Fifty actual failures across three baseline runs now pass. Fixtures advance the
application clock after real PostgreSQL repository/journal acquisitions. Four independent
controls already passed: disabled-MFA binding/import admission and old transfer/
cancellation receipt recovery after a company-date change. The correction changes
fifteen People and six Organization use cases, plus Organization's shared DI.

Guarded identity snapshots retain session assurance through the final journal or
receipt acquisition. Binding and import admission now require current authenticator
enrollment as well as recent proof. Original/live grants, ownership, independent
binding and ordered guards remain intact. Mutable lifecycle task lists and initial
assignee maps are bounded and copied before validation, hashing and waits.

Transfer retains both companies' original/live scopes, recent assurance and current
immediate-transfer dates through completion. Future-revision cancellation rechecks
its company date. Their old receipts remain recoverable after dates change; new
mutations cannot use yesterday's admission. Client availability remains an
admission policy. No new query, schema or background MFA-age policy was added.
GetCompany/CreateCompany/UpdateCompany now receive the existing security policy
and clock through Organization's shared API/worker configuration.

The 40-case baseline had 39 actual failures (92s); the enrollment baseline had ten
failures and two controls (49s); the cancellation-date baseline had one failure and
one control (33s). All 54 focused API cases, 50 People domain, two Organization
domain and fourteen People repository cases passed with quality/packages in 142s.

The broad API selection passed 497 cases without failures, errors or skips.
Its parallel worker run exposed two legacy fixture failures: the import operator
had recent proof but no stored authenticator enrollment for interactive admission.
The combined build was therefore failed (1329s), not accepted as a green
run. After correcting only that fixture, the complete worker suite passed 104 cases
with the separately accepted manual one-hour test skipped. Quality and both packages
passed in 226s. Production changes and package hashes stayed identical
to the successful broad API selection; both actual application contexts are covered.

Three actual API/browser workflows passed in 36.1s with setup (CSV imports,
employment transfer, account binding), and two API/worker/browser workflows passed
in 36.8s (scheduled onboarding/offboarding). [Measurements](measurements/people-command-assurance-2026-10-30.json)
retain exact reproductions, controls, suites, fixture correction and package hashes.
The earlier full CI at `70b92e59` excludes this correction. Correction `158b81ac`
is committed/pushed and running in [CI 41032184925](https://github.com/fajarxfce/hris-platform/actions/runs/41032184925)
for exact source `158b81acce89f637dbf715bb573fffb7b99b92c9`. Observe the terminal
result while continuing the missing document metadata feature.

All five People rows remain unverified. Implement the confirmed
[document category and identity-validity gap](document-identity-metadata-gap-2026-10-30.md),
finish the detailed People review and continue remaining Platform/Mobile work.
The private schema-101 API, accepted Google binding and phone were preserved.
