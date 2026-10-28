# Report exports

Export delivery remains in progress. Reporting owns its selections, generated data,
job progress and retention; it cannot mutate operational facts. Every export must
retain its source definition, owner/company scope and execution snapshot. Downloads
must recheck current source access and expiry. Catalogue/filter delivery is recorded
in [reporting](reporting.md); the full requirement remains in
[the reporting module](modules/reporting.md).

## Selection and CSV encoding

`ReportSelection` and `validateReportSelection` now provide a named pure selection
contract for the seven current report kinds. Saved-filter validation delegates to it
and retains its existing name rules, range failures and normalization. Empty headcount
selections mean the anchor company; explicit groups remain detached and unchanged
in membership. This selection contains no authority to its sources.

The pure `report-csv.v1` encoder consumes typed text, integer, exact decimal or empty
cells against ordered named columns. Column keys are unique lowercase identifiers,
bounded to 64 characters; a part accepts at most 64 columns and 500 source rows.
Text is limited to 4,096 UTF-16 code units per cell, with valid surrogate pairs and
no ISO controls except tab/CR/LF. Decimals retain up to thirty significant digits
and scales from -9 through 9; scientific input is rendered as plain decimal without
binary floating-point conversion or rounding. A nullable column explicitly permits
an empty cell; other missing or mismatched values fail validation.

Records use UTF-8, quoted text with doubled quotes and CRLF boundaries. Only the
first part writes a UTF-8 BOM and header. Numeric negatives retain their numeric
representation. Text that may be interpreted as a spreadsheet formula receives a
leading apostrophe; detection considers leading whitespace, formatting and combining
code points, including supplementary Unicode characters. Quoting alone is insufficient
to establish literal spreadsheet text. Other text preserves its content.

Each part owns immutable bytes and contains at most 1 MiB. If the next complete
record would exceed that limit, encoding returns the accepted prefix and its exact
row count. A caller must advance only by that count and retain the unconsumed rows;
it cannot advance to the end of the fetched page. Nonempty valid input makes positive
progress. An empty initial export produces only the BOM/header, while an empty later
part is rejected. Invalid shape/text encountered during encoding returns a failure,
not a partially accepted result. Consumers publish the complete export only after
all of its parts and manifest have been verified.

The byte/row bounds are contracts, not measured process-memory or workload claims.
Private storage currently accepts at most 5 MiB per object, so an encoded part fits
without enlarging the shared storage limit. The worker must also bound its source
page acquisition, staged rows, retained parts, attempts and total export size.

## Source transaction boundary

`ReportSourceTransactionRunner.runWithReportSources` retains the requesting actor's
company for owned capture and journal writes while installing an explicit, detached
selection of one to 32 source companies. The anchor need not belong to that set.
V94 extends the existing report SELECT policies to support this anchored context;
only employments, employment revisions/cancellations and client-policy heads/revisions
gain selected-company reads. Source-company writes and private profile reads gain no
additional scope. Transfer context is cleared on entry. Ordinary, transfer and global
report transactions continue to install their own explicit contexts.

This is a transaction capability, not authorization or an export endpoint. The
owning use case must check the anchor and every source's original/current grants,
credentials, client policy and assurance after ordered guards, and retain those
checks through capture and journal completion. A source query must filter the exact
selection even when the anchor's ordinary reads are also visible. Existing global
group reads still require an actor with no company. Transaction-local settings are
discarded on success, failed results or cancellation, and nested owners are rejected.

The port keeps READ COMMITTED isolation. It allows one coherent source acquisition
and its owned staging writes to commit together; it does not make independently
paged live acquisitions into a historical snapshot. Large-detail capture and its
retained row store remain part of the export implementation below.

## Retained snapshots

V95 introduces Reporting-owned snapshot headers and numbered rows, both protected
by company **and** account RLS. Snapshot IDs are unique within a company, including
between owners. The header retains the canonical selection, dataset,
ordered typed columns, `report-export.v1` schema, CSV format, evaluation timestamp and
original expiry. Capture timestamps use PostgreSQL's exact microsecond precision;
expiry must follow evaluation by no more than 168 elapsed hours, independently of
the database timezone or daylight-saving transitions. The schema and company
selection are detached before I/O. These metadata and persistence ports confer no
source permission and are not exposed as export endpoints.

`begin`, row acquisition/insertion and `seal` belong to one use-case-owned transaction
under current source/access guards. Sealing verifies the exact stored row/byte totals
and a gap-free sequence starting at one. A deferred database constraint prevents an
unsealed header from committing, including an interrupted or incomplete capture.
Headers and rows become immutable; a competing begin cannot replace an existing
identity or deadline. Empty captures seal with zero rows and retain their CSV schema.
Known source projections can insert their rows with one coherent `INSERT ... SELECT`
before sealing. The seven report-specific projections and their queued orchestration
still need to be connected; successive live reads are not a snapshot strategy.

One snapshot holds at most 100,000 rows and 100 MiB of staged JSON, independently of
the eventual CSV limit. Database accounting updates once per insertion statement and
enforces the aggregate quota before commit. Stored row byte lengths also let a read
return a complete-row prefix of at most 500 rows **and** one MiB of cell JSON. A caller
advances by the returned rows, never the requested page size. Nonempty remaining
captures must make positive progress; gaps, invalid types or malformed pages fail
without returning partial data. Application appends validate all cells before I/O
and retain at most 500 rows/one MiB per batch.

Cells store text or null in JSON, with the retained column type distinguishing text,
integers and decimals. This avoids dependence on a parser's floating-point settings.
Decimal values and scale survive storage, including negative scales; plain numeric
CSV formatting and spreadsheet-text protection happen at rendering. Mapping retains
the shared failure/cancellation boundary and returns detached row/cell collections.

Expired row cleanup is owner/company scoped, takes a bounded batch with `SKIP LOCKED`,
and also checks the database clock. An arbitrary future timestamp cannot delete live
facts. Cleanup failure rolls back its batch; the immutable header, original totals,
identity and deadline remain after the rows are retired. Scheduling that cleanup,
current-authorized content delivery and export history still belong to the full
workflow. These byte/row contracts do not establish measured process-memory targets.

## Report layouts and source capture

The server owns the ordered columns for seven summary datasets and three detail
datasets. Capture rejects a different column list, definition or format version.
Every row retains the definition version, evaluation instant and selected date or
month window; monetary layouts identify IDR. Instants use UTC with six fractional
second digits. Counts and exact decimal quantities remain separate CSV cell types.
Summary rows explicitly identify `TOTAL`, `COMPANY`, `DAY`, `STATUS` or `MONTH`.

| Dataset | Retained summary | Detail rows |
| --- | --- | --- |
| Headcount | Distinct global person count and exactly the selected company buckets | Unsupported |
| Attendance exceptions | Coherent total and date counts | Unsupported |
| Leave | Status counts, exact half-day units and decimal days | Submitted requests with window and whole-request quantities |
| Overtime | Selected status, clipped month boundaries and requested/actual/approved minutes | Unsupported |
| Expenses | Current status counts and latest submitted amounts | Latest submission identity, frozen labels/title, window and whole-submission amounts |
| Payroll costs | Published month counts and exact signed monetary totals | Unsupported |
| Attendance closings | Publication references, original facts and latest corrections | Employment-month rows with exact closing and selected adjustment references |

Headcount conversion preserves the aggregate's distinct global person count rather
than summing company counts. The transaction's anchor is not implicitly added to an
explicit company selection. An empty published closing retains its publication
identity in the summary; an unpublished month has no closing reference. Detail rows
represent qualifying employments only. Summary builders return detached, bounded
rows and validate them with the existing typed CSV encoder.

Leave, expense and closing capture share named SQL fragments with their live report
readers. Each detail capture inserts from one SQL source snapshot; it does not read
successive live pages or materialize all rows in the JVM. Leave and expense rows
order by descending submission instant and UUID; closings order by month and
employment UUID. The capture retains current status and labels at acquisition,
frozen source facts and selected revision references. Later operational changes do
not rewrite those rows. Future hires, partial closings and unsent replacement drafts
follow the existing report definitions.

The caller must own source authorization and the begin/capture/seal transaction.
Before acquisition, the repository verifies the exact open header, owner, selection,
layout and deadline. SQL examines at most the row limit plus one output candidate
and admits no rows if the count, stored JSON bytes or source validity fails. A
failed Result must roll back the transaction, including its new header. Sealing
still validates the immutable store's sequence and quotas. The database may need
to scan/aggregate/sort additional source records; these output bounds are not a
measured query-memory or workload guarantee.

## Execution records and private parts

V96 stores an owned export request separately from its immutable captured facts.
The request retains its selection, dataset, schema/format, original deadline,
client transport/build and narrowly selected original source grants. It includes
the anchor plus every selected source company; these stored grants confer no
current authority. Admission and every content operation must still intersect them
with current access. Admission must generate a fresh export UUID on the server,
independently of the caller's idempotency key: that UUID also identifies its cleanup
resource and must not be chosen to collide with another module's resource.

The execution record progresses through queued, rendering, ready or stopped states.
Each mutation observes a version. A captured snapshot can attach only when its
owner, exact definition/columns and original expiry match the request. Recovery
requires a new queued job for the same owner/export and a failed or cancelled prior
job. It preserves the existing snapshot, accepted parts, row offset and deadline.
Raw row/byte progress belongs to immutable part triggers; callers cannot edit it.

Each upload attempt reserves a fresh company-prefixed key under
`company/reports/export/upload`. A matching pending cleanup reservation must already
exist before the upload reservation can commit. Its deletion deadline is at least
one hour after the export's fixed expiry. The caller performs provider I/O after
commit, then reacquires job/owner/export and access guards before accepting the
exact reserved part. Acceptance matches its identity, row/byte offsets, byte count,
digest and creation instant. Row locking rechecks the observed version after a
competing write. Late responses cannot replace an accepted part. Cleanup remains
scheduled for both accepted and abandoned objects.

Limits are enforced in pure validation and PostgreSQL: two active and ten physically
unretired exports per account/company; eight execution jobs per export; 1,024 accepted
parts, 1,088 total reservations, and 256 MiB cumulative reserved bytes. Individual
parts remain at most one MiB and 500 rows; accepted output remains at most 100 MiB
and 100,000 rows. The cumulative reservation budget does not reset on failure or
recovery. Stopping an export releases its active slot while retaining its physical
retention slot. Failed cleanup cannot silently free that slot for more retained data.

Publication requires all captured rows in a contiguous, ordered part manifest.
An empty dataset has one header part. The opaque manifest tag hashes the ordered
part descriptors and provider versions; it is not a SHA-256 of the concatenated CSV.
Retirement requires database-clock expiry, absent captured rows, no remaining
cleanup reservations and, for attempted uploads, the one-hour grace. The original
request, final metadata and part evidence remain immutable after retirement.
History reads use a bounded owner-scoped keyset page.

These are persistence and execution contracts. Public admission, current-authorized
worker orchestration, provider writes, downloads and scheduled retirement are still
being connected; this storage increment does not expose an export job or endpoint.

## Validation and remaining delivery

Validation passed 80 reporting tests (35 domain, 43 data, two delivery) and 26
saved-filter API/PostgreSQL cases across focused runs. The eight added domain cases
cover shared selection semantics, detached groups, exact CSV golden bytes, Unicode
formula treatment, nullable/type errors, malformed Unicode, exact one-MiB admission,
UTF-8 byte limits, immutable output, one header across parts and 1,251 ordered records
consumed through repeated bounded pages. The final domain/packaging run passed after
extending formula handling to supplementary formatting and combining code points.
Architecture and formatting checks passed. The initial test compile used an unavailable
assertion library; tests were changed to the repository's existing JUnit assertions.

The anchored source boundary subsequently passed 24 database/migration tests,
11 group-headcount API/PostgreSQL cases and all 82 worker tests. Three new database
cases cover selected reads on all five permitted tables, denied source mutations,
private-profile exclusion, owned capture/journal writes, failure/cancellation/late
interruption rollback, reused connections, cleared transfer context, detached and
bounded selections, and rejected nested transaction owners. API and worker contexts
both resolve the new port to their existing transaction owner. Both application JARs,
architecture and formatting checks passed in the same run. Test containers were
disposed. This verifies the transaction boundary, not a running export workflow.

The retained row store subsequently passed 89 reporting tests (38 domain, 49 data,
two delivery), 24 database/migration tests, 48 server/PostgreSQL cases and all 82
worker tests across focused runs. Eleven new application-context/PostgreSQL cases cover
1,251 retained rows, empty captures, long Unicode byte-prefix pages, owner/company
isolation, competing capture and cleanup, cancellation/rollback, denied mutation and
early deletion, exact original metadata after retirement, and real SQL row/100-MiB
quota rejection. A one-query capture remains unchanged after its source is edited.
One focused run repeated ten snapshot cases and 49 data tests after fixing
an append separator count, including exact one-MiB acceptance and next-byte rejection.
Numeric mapping preserves negative scales and rejects malformed or missing pages.
A final run passed all 24 database tests, eleven snapshot cases and the six-case
worker integration context. It verifies the exact lifetime across both New York
DST transitions, including raw SQL rejection of an extra second.
Both applications packaged successfully; architecture and formatting passed. Initial
runs found a Jackson `map` overload compile error and a connection-failure fixture
missing its SQLState; both were corrected without changing shared failure semantics.

The report-specific layouts and capture subsequently passed all 99 reporting tests
(44 domain, 53 data, two delivery) and 41 selected server/PostgreSQL cases. Six new
pure-domain cases cover all ten layouts, exact source units, group distinct persons,
selected companies, signed money, clipped overtime months, publication references
and detached bounded output. Four repository cases cover exact open-header matching,
date/byte limits, failed or partial acquisition and preserved cancellation/error
classification. Four new runtime/PostgreSQL cases exercise all three detail sources,
empty captures, half-day and decimal rendering, SQL overflow without prefix insertion,
rollback, future-hire exclusion and retained rows after withdrawal, draft changes,
resubmission and successive attendance corrections. Existing live readers and all
eleven retained-storage integration cases remain green. The six-case worker context
and both application packaging tasks remained up to date; Reporting's worker import
graph did not change. Architecture and formatting checks passed, and fixture containers
were disposed. Initial runs corrected only new-test syntax, required fixture values
and an inherited helper-name conflict before the integration run passed.

Execution-record validation subsequently passed 109 reporting tests (50 domain,
57 data, two delivery), 24 database/migration cases, 21 server/PostgreSQL cases and
all 82 worker tests. Six new domain and four repository cases cover authority and
client metadata, exact time/state/size bounds, malformed acquisition, ordered
manifests, empty output, detached data and cancellation/failure classification.
Six new runtime/PostgreSQL cases cover retained parts through explicit recovery,
exact snapshot attachment, required cleanup reservations, signature/version fencing,
competing reservations, owner isolation, active/retained quotas, keyset history,
expiry blockers and immutable evidence. Raw SQL admits exactly 1,088 reservations
or 256 MiB of reserved bytes, then rolls back the next reservation and its cleanup
row together. A final focused run repeated 57 data, 24 database, six export storage
and six worker-context cases after strengthening duplicate insertion and publication
checks. It proves a partially rendered file cannot publish and repeated insertion
consumes no additional slot at quota. Both packages, architecture and formatting
passed, and test containers were disposed. The initial migration compile found an
ambiguous PL/pgSQL CASE expression; parentheses corrected it before the successful
runs. Tests exercised storage records and cleanup evidence, not new provider uploads
or public export admission.

Request admission, private artifact publication,
fenced worker execution, interruption recovery, scheduled expiry/cleanup, authorized
downloads and export
history/dashboard remain to be implemented and validated. No export endpoint or worker
kind is exposed by these foundations. Reporting has not yet been added to the worker
import graph. No new browser, provider, workload or deployment acceptance is claimed.
