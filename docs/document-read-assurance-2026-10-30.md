# Document read completion — 30 October 2026

The full requirement review found seventeen older Document readers that checked
MFA before their final acquisition but did not retain that check through completion.
At `a45d5a9b`, nineteen PostgreSQL-backed variants returned acquired metadata or
bytes after expiry. One further variant rejected the eventual result but still
started an object read after its preparation proof expired. All twenty genuine
reproductions failed, then passed after correction (48s baseline; 52s corrected).
A test-only missing repository override parameter was fixed before that baseline.

The correction covers header, directory, revision history (including its final
job lookup), inspection attempts, download metadata and bytes; administrative
retention state/history/policies and inventory state/pages/attempts; employee
search/exact selection; and owned employment/evidence discovery. Existing exact
revision, retirement review and self-evidence record readers already check at
completion and remain unchanged.

Each reader retains its guarded identity projection and checks current session
assurance after successful acquisition. Content preparation checks before object
I/O begins; the second transaction rechecks availability, ownership and assurance
through its final profile acquisition before returning bytes. Storage remains
outside SQL transactions, with the same part/range/integrity/deadline boundaries.
No additional repository query, DI dependency, schema, write or retention state is
introduced. Original/current grants, limits, cancellation and failure boundaries
remain. Tests verify renewed reads, exact content, no unexpected storage call and
unchanged revision values, metadata counts, audit, outbox and operation receipts.

All 223 selected API cases, 18 domain cases and nine data cases passed, alongside
formatting, architecture and API/worker packages (400 seconds). Four actual
API/browser workflows passed in 42.4 seconds with setup: resumable upload and
cancellation, retention policies, archive/restore/hold, and accepted retirement
including a competing hold. The [measurements](measurements/document-read-assurance-2026-10-30.json)
retain exact counts, baseline failures and package hash. The existing API/worker
graph already has full context acceptance; this correction changes no DI.
Final-source CI follows the command review without cancelling the approval run.

At the read increment, Documents was not yet accepted as a whole. Review of older upload, validation,
inventory and retention commands also found missing assurance checks after their
journal writes; reproduce their late-expiry behavior and preserve rollback,
original receipts and bounded physical-attempt cleanup before closing the module.
The subsequent [command correction](document-command-assurance-2026-10-30.md)
and [module review](documents-feature-review-2026-10-30.md) now have local acceptance.
The full audit retains 41 unverified initial rows and the full Mobile review.
The running private stack and phone are intact.
