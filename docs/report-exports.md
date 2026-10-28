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

Request admission and durable snapshots, private artifact publication, fenced worker
execution, interruption recovery, expiry/cleanup, authorized downloads and export
history/dashboard remain to be implemented and validated. No export endpoint or worker
kind is exposed by this foundation. It changes neither the worker import graph nor
the dashboard; no new worker-context, browser, provider or deployment acceptance is
claimed here.
