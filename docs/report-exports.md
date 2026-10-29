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
before sealing. The queued worker connects the known projections to this capture transaction;
successive live reads are not a snapshot strategy.

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

These persistence contracts support the worker below. Public command/history
routes, current-authorized downloads and scheduled retirement remain open.

## Admission and worker execution

`ManageReportExports` owns idempotent admission, observed-version cancellation and
explicit recovery. Admission creates a new server export UUID independently of the
command key, captures narrow original grants before resource waits, and checks each
source plus the anchor under ordered client-policy/company/member/account guards.
Current account credentials, original/live grant intersection, MFA and effective
client availability are checked again after waits and journal work. Owner limits
and the company queue's 100-pending-job limit share the mutation transaction. A
response replay returns the same receipt without another queue item, slot or event.

Recovery requires the old job to be failed or cancelled and preserves the original
selection, source grants, client context, expiry, sealed facts and accepted parts.
The current caller and original client must both pass current availability. A new
job uses the current session's credential version; recovery cannot widen original
source grants or prolong retention. Owner cancellation needs current `reports.read`
and interactive assurance, so lost source permissions do not prevent stopping work.
It immediately stops the export, requests job cancellation and leaves all cleanup
reservations intact. HTTP adapters for these commands remain in the next increment.

Reporting is now imported by both applications. The registered `REPORT_EXPORT`
worker resolves its original account/session credentials with background command
authority; interactive MFA proof age is not a durable job credential. Each step
locks the job lease, owner and export before sorted source/access guards. It
revalidates current credentials, original/live grants, policy transitions and expiry
through capture, rendering and publication. One aggregate query supplies each small
summary; each large detail capture uses the existing single-SQL source projection.
Sealed snapshots survive retries and never reacquire live facts.

Rendering reads bounded retained pages and advances by the CSV encoder's consumed
row count. Each attempt gets a fresh object key and durable cleanup reservation
before provider I/O outside the SQL transaction. The acknowledgement must match
key, byte count, SHA-256 and a bounded safe provider ETag. Acceptance obtains a new
fenced transaction and rechecks lease, cancellation, version, source authority and
expiry. Failed or late acknowledgements leave a scheduled orphan; accepted keys
are never overwritten or removed from expiry cleanup. Actual database-clock expiry
also fences mutations even if an application clock is behind.

Job progress uses an upper bound of 1,026 steps: snapshot capture, up to 1,024 parts,
and final publication. An empty dataset still uploads its header and advances.
Recovery can resume at the retained global part position. Publication validates
all ordered manifest descriptors, rows and total bytes and commits `READY`, the
successful job, checkpoint and journal together. Failure cleanup can stop a leased
job after permission loss, without reopening source access. A stale lease cannot
change a newer attempt. Generic exhausted-job recovery also handles a stranded
queued/rendering export whose previous job is already terminal.

## Owned history and authorized downloads

The public API under `/api/v1/companies/{companyId}/reports/exports` provides:

| Method and suffix | Contract |
| --- | --- |
| `POST` | Start a server-owned export from a canonical selection and `SUMMARY`/`DETAIL` dataset; requires `Idempotency-Key`. |
| `GET` | Owned history, newest first; `size` is 1–50, with paired `beforeAt`/`beforeId` keyset cursor. |
| `GET /{exportId}` | Owned status, selection, progress, current job outcome and fixed expiry. |
| `POST /{exportId}/cancel` | Idempotent cancellation with the observed `expectedVersion`. |
| `POST /{exportId}/resume` | Idempotent recovery with the observed version and current source/client authority. |
| `GET /{exportId}/content` | Authorized CSV download, including implicit `HEAD`, Range/If-Range and conditional ETag responses. |

History, detail and cancellation require current owner/company access, `reports.read`
and live MFA. Their explicit HTTP availability exemption lets owners inspect and
stop retained work after a source grant or reporting availability changes. It does
not exempt authentication, company membership, credentials or MFA. Start, resume
and content retain normal client availability checks. Status responses omit saved
grants, client details, storage keys and credential information.

Downloads validate the sealed snapshot, complete immutable part manifest, byte/row
counts and manifest-derived ETag. Each response block is at most one MiB; it can
cross multiple stored parts. Every acquired object is at most one MiB and must match
its retained SHA-256 and length before slicing. Provider reads occur outside SQL;
a new guarded transaction rechecks credentials, original/current source authority,
MFA, client policy, expiry and the pinned export version/tag after each object read.
The shared binary transport owns four response slots, a 60-second deadline,
interruption and no-store headers. No public object URL bypasses those checks.

## Scheduled retirement

V97 captures a minimal expiry queue on admission and backfills unretired exports.
The global worker can discover only queue identities and deadlines. Snapshot facts
and execution records retain owner/company RLS; updating or removing queue entries
requires the corresponding scoped worker transaction. API actors cannot discover or
reschedule even their own queue entries. Database guards retain immutable identity
and expiry, bound postponement, and reject queue deletion before physical retirement.

Each scheduled pass selects at most four due entries. Independent transactions take
job, owner, export and queue locks, stop expired active execution, request job
cancellation, delete at most 500 expired snapshot rows and attempt retirement.
Cleanup reads minimal raw metadata, so an unknown future report format and an
inactive owner do not prevent deletion. Database-clock expiry remains mandatory.
Retirement waits for absent rows, completed object cleanup and the original one-hour
grace where uploads were reserved; immutable metadata remains available to its owner.
Private objects use the existing cleanup worker and durable reservations.

Progress schedules another pass after one second; waiting for objects uses 60
seconds. A failed item rolls back business changes and journal, then independently
postpones its unchanged observed queue entry for 60 seconds. Other selected items
still run. Competing workers cannot overwrite a newer schedule or double-delete
rows. One owned timer runs with a one-minute fixed delay, interrupts outstanding
work on shutdown and cannot restart after disposal. Disabling worker execution
allocates neither this worker nor its timer.

## Dashboard workflow

Each of the seven report pages offers export from its applied filters. The review
retains that canonical selection independently of unapplied form edits, and lets
users choose summary or supported detail contents. Creation uses a fresh command
key while the server assigns the export identity. The review explains capture time,
fixed expiry and per-owner limits. Catalogue and navigation link to owned history.

History replaces one 20-item keyset page at a time, with older/newest navigation.
Detail shows current progress, processing outcome and expiry, confirmed cancellation
and eligible explicit recovery. Refresh is user-driven; no background polling is
allocated. Reads clear obsolete metadata on refresh, failure, hidden workspace and
account/company replacement. Status/cancellation work with report permission after
source access is lost; creating, recovery and download additionally require source
access, with authoritative checks on the server.

A screen owns at most one command and retains its detached input, observed version
and original key after an uncertain response. Recovery through MFA and later
rejections never silently replaces that command. Leaving an unresolved command is
protected; disposal clears it and suppresses late results. Download uses the shared
bounded binary HTTP and browser file ports, validates exact size/media type and
export-bound manifest ETag, and rechecks expiry before browser delivery. Buffers
never enter UI state and are erased after completion, cancellation or failure. One
owned download is cancelled on refresh, hidden workspace or disposal; denials remove
stale detail before another action. English/Indonesian copy and narrow dark layouts
use the existing portal components.

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

Export execution validation passed 226 distinct tests across focused runs: 111
Reporting (52 domain, 57 data, two delivery), two core-job domain, 29 API-context/
PostgreSQL cases and 84 worker cases. The complete existing 82-test worker suite
ran after importing Reporting; two additional tests exercised the registered task
through real Spring Batch and credential revocation before actor resolution. Both
application JARs, architecture and formatting checks passed.

Eight new API-context/PostgreSQL cases cover all seven summaries and three empty
details, exact selected-source scope, server-generated IDs, concurrent owner quotas,
response replay, orphan cleanup, cancellation during provider I/O, retained facts
and parts across recovery, invalid provider receipts, revocation, credential changes,
lease loss, expiry and disabled source policy. A synthetic 1,201-row wide capture
exercises CSV byte-prefix progress, no skipped/duplicated rows, lost upload responses
and recovery without recapture. Real source SQL is covered separately by the existing
leave/expense/closing capture cases. Injected audit failure rolls back admission,
capture and final publication. MFA expiry during journaling rolls back each of start,
cancel and resume. Test providers assert that every put occurs outside a SQL transaction
and after its durable cleanup reservation. The final eight-case run repeated these
execution checks after adding assurance coverage. Initial runs corrected an encoder
API invocation and fixture clock/grant/Batch-schema assumptions before passing.

Those execution tests use controlled storage. Public access and scheduled retirement
were subsequently validated as described below.

Access/retirement validation passed 338 distinct tests across focused runs: 115
Reporting (52 domain, 61 data, two delivery), 11 core HTTP, 24 database, 98 server and
90 worker cases. Both application JARs, architecture and format checks passed. The
entire worker suite exercised the changed dependency graph. The broader run had one
incorrect HTTP validation expectation; after correcting it from 400 to 422, all
seven export HTTP cases passed in the final focused run. Earlier fixture corrections
fixed a cleanup-lease accessor, staged-row column and the mistaken assumption that
two simultaneous four-item scans must choose different entries.

HTTP tests exercise private keysets, another member's exclusion, command replay,
status/cancellation after source or availability loss, and 1,201 retained rows across
three CSV parts. Full/range/conditional/HEAD responses, short/corrupt provider bytes,
and credential, permission, policy, MFA or expiry changes during reads are covered.
Runtime API RLS cannot discover or disable its own expired queue entry. Six retention
worker cases cover unknown formats, deactivated owners, bounded deletion, fairness,
competing workers, object-cleanup dependencies, atomic audit rollback, scoped queue
policies and interruption before commit. Test containers were disposed.

Dashboard validation subsequently passed all 3,910 unit/controller/UI tests, with
136 Reporting tests repeated after fixture cleanup. Twenty-six distinct Chromium
cases passed across focused runs, including eight new export scenarios. They cover
applied filters, exact 1,201-row CSV download, original-command recovery through MFA,
observed cancellation/resume, source loss, bounded keysets, late company reads,
expired/denied content and Indonesian dark narrow review. The broader browser run
initially navigated before the existing one-second browser URL lifetime ended;
waiting for the completed download status fixed that test, and all eight export
cases passed again. Two display-copy cases subsequently passed after removing
implementation terms from product copy. Architecture, Biome, strict TypeScript and
production packaging passed; the existing Vite chunk-size advisory remains.
Initial fixture corrections addressed optional TypeScript fields and a cursor's
seven-day expiry boundary. Desktop/mobile screenshots were inspected.

### Real private-storage browser acceptance

An isolated Chromium workflow now runs the actual API, PostgreSQL, worker and
Garage v2.4.1 S3 service. It creates independent MFA administrator/reader accounts,
applies headcount filters, and drops the first admission response after the server
commits. Explicit retry retains the same operation/input and returns the same
export without another history record. The worker publishes a real private object;
the browser downloads its complete CSV. Full, conditional, HEAD and byte-range
responses agree with its retained length and SHA-256. Adding another employment
changes the live report but leaves the captured download and ETag unchanged.

The administrator cannot discover or download the reader's export. Anonymous
access to its exact S3 object is denied. Removing the reader's source permission
retains owned status but denies conditional/HEAD content requests and removes the
download action after session refresh.

The same workflow exports 1,201 leave detail rows with exact 600.5-day totals.
One request is created through the API; 1,200 fictional cancelled historical
requests and their matching approvals/change records are provisioned directly in
the owned fixture database. This exercises real capture SQL, not 1,201 leave
commands. Three stored parts contain 500, 500 and 201 rows. The complete 399,053-byte
CSV has unique request identities and correctly escaped quoted/comma names; every
part matches its recorded byte range and digest. A range crossing two S3 objects
returns the exact requested bytes. Withdrawing the API-created request afterward
does not change the retained download.

The final workflow passed in 17.3 seconds (43.1 seconds including service/build
setup). Five storage-fixture lifecycle tests cover private configuration, normal
disposal, lost creation responses, interruption, startup timeout, failed startup,
non-loopback bindings and failed-removal retry. Architecture checks, four dashboard
architecture tests, Biome, TypeScript and production build passed. Earlier
acceptance runs corrected fixture-only permission and leave-version field names.
Screenshots were inspected and all owned services/configuration files removed.

Run `npm run test:integration:exports` in `apps/dashboard` after building both
application JARs with JDK 21. Docker and Playwright Chromium are required. The
optional storage fixture creates its own named container, ephemeral credentials,
loopback-only port and bounded memory/tmpfs; no deployment bucket or data is used.
CI runs this separately from the existing worker suite. This verifies local real
S3 execution and access. Real storage interruption is now covered below; physical
expiry retains the earlier controlled-storage/backend tests. Production provider
acceptance, reference-workload measurement and deployment are separate work.

### Real storage response loss and resumed output

The export fixture now owns an optional loopback proxy in front of its real Garage
container. It forwards signed requests without changing their destination or
payload. After arming, the first PUT succeeds normally and the second is consumed
by Garage before its response is dropped. Further provider requests are refused
until the test explicitly restores transport. Private command files and bounded
safe counters expose no provider credential, request header or object path.

The worker retained 500 accepted rows and the uncertain second upload while its
existing bounded transient retries remained eligible. The owner cancelled that
run through the browser and waited for `CANCELLED`/`STOPPED`; after transport
restoration, Resume created attempt two. The test also dropped the committed
resume response and retried the same operation/input, receiving the same receipt.
The first accepted upload identity was unchanged, the original expiry remained,
and the final three parts contained 500/500/201 rows and 399,053 bytes. All 1,201
identities were unique, all part ranges/digests matched and later source withdrawal
left the file unchanged. The uncertain provider object remains governed by the
existing cleanup reservation; this experiment does not measure physical expiry.

The complete actual API/PostgreSQL/worker/Garage/Chromium workflow passed in 20.4
seconds (45.9 including setup). Nine storage-fixture/proxy regressions passed,
including lost responses after real socket forwarding, explicit recovery, request/
control bounds, four-connection capacity and interrupting/joining a blocked
provider read on shutdown. Buffers are capped at 2 MiB per request/response and
each handler has a 15-second total transfer deadline. The fixture closes owned
sockets/threads before removing its provider and private files. All owned services
and private connection/control files were verified removed after this run.

Architecture, Biome, strict TypeScript and production build passed. No application
JAR or runtime DI graph changed. Initial fixture runs incorrectly expected a
transient error to become terminal immediately, then used an ambiguous status
locator after a successful resume; both were corrected before the complete pass.
Evidence: [sanitized report](measurements/report-export-storage-recovery-2026-10-28.json),
`.work/storage-recovery-browser-3.log` and `.work/storage-fault-tool-final.log`.
The stopped/export screenshots were inspected. CI now runs all nine tooling cases
before this same export workflow; no hosted CI result is inferred from that change.

## Real-time retention acceptance in progress

[The opt-in elapsed-time fixture](export-retention-acceptance.md) now compiles and
is running against actual PostgreSQL/Garage. It preserves the one-hour physical
grace and immutable deadlines, checks a live control, and recovers a lost delete
result before releasing quota. Final acceptance remains pending until its bounded
run completes; the existing controlled checks are not replaced by compilation.

## Actual elapsed retirement acceptance

The [elapsed PostgreSQL/Garage case](export-retention-acceptance.md) passed one
unmodified physical grace hour on 29 October 2026, including a lost successful
delete response, finite retry, one retirement audit and released expired quota.
The live control export and inactive owners were preserved. JUnit recorded one
pass in 3,860.892s at schema 100; public seven-day retention was unchanged.
