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

Request admission and durable snapshots, private artifact publication, fenced worker
execution, interruption recovery, expiry/cleanup, authorized downloads and export
history/dashboard remain to be implemented and validated. No export endpoint or worker
kind is exposed by these foundations. Reporting has not yet been added to the worker
import graph. No new browser, provider, workload or deployment acceptance is claimed.
