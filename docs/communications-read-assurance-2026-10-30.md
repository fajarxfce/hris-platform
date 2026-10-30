# Communications read completion — 30 October 2026

The full feature audit found that ten older interactive Communications readers
checked session assurance before acquiring their content but did not check it
again afterward. Thirteen PostgreSQL-backed variants at `3fd05b8e` returned
acquired content or metadata after the MFA proof expired. All thirteen failed
before correction; all passed after correction, including successful fresh reads
and explicit reacquisition after renewed proof.

Affected reads cover announcement and audience-group directories, current and
retained revisions, announcement history, audience preview, audience-reference
search and selected labels, Inbox directory/detail, and the unread aggregate.
They now retain the guarded access projection and validate the current proof at
successful acquisition completion. Initial permission checks and original/current
permission intersection remain. Existing guards prevent concurrent account,
membership and resource changes from invalidating that projection. Preview keeps
its original evaluated date and count; renewed MFA does not reserve an audience.

The change adds no query, dependency, retry, write, schema, queue or retention
state. It does not mark an Inbox item read or acknowledged. Acquisition failures
and cancellation keep their existing transaction boundaries. Publication review
and administrative delivery already checked assurance after acquisition and need
no correction. Background workers retain their separate execution authority.

All 106 selected API tests, 13 domain tests and three repository tests passed in
234 seconds, alongside formatting, architecture and API/worker packaging. These
include capacity, immutable publication, receipt replay, concurrent access,
archival, account-owned synchronization and email/push capture. The full worker
context was already accepted with the shared DI change; this correction changes
no DI or worker implementation. The actual API/worker/browser/SMTP workflow also
passed on the resulting package; exact timing is retained in the
[measurements](measurements/communications-read-assurance-2026-10-30.json).

See the [module requirement review](communications-feature-review-2026-10-30.md).
Final source CI follows the current approval run without cancelling it. The
private development stack and installed phone retain their earlier acceptance.
