# Company thread publication cancellation

Mobile's Company thread observer now keeps each queued local snapshot inside the
admission reader cancellation boundary. A slow consumer cannot receive a snapshot
from the retired reader after confirmed Company denial. Parallel context/history/
command reads and optional recipient metadata retain their existing behavior.

The exact regression failed before the fix (three values instead of two after
both readers retired) and passes unchanged afterward. Full validation accepts
14,688 cases, including12,650 fresh executions and2,038 unchanged reports. All10
SDK8 native scenarios pass against the exact current-source proof APK in476.311s,
using a fresh2560MiB/two-core emulator without concurrent compilation.

[Implementation and evidence](../measurements/collaboration-thread-publication-lifetime-2026-11-12.json)
link the Mobile commit, source/report hashes, retained failing candidate and native
audit. No Group server source or live deployment changes. Release92 packaging follows
in Mobile; physical installation and phone behavior remain separate acceptance.

Hosted control run43297910558 validates exact Mobile source151438b4 independently.
Its portable job passes12,666 cases:6,058 desktop,955 Android host and5,653 browser.
The artifact digest, ZIP integrity, full case inventory and prior test identities are
verified with zero replacements or browser-name normalization. Apple job131478499227
also passes5,187 native iOS shared cases, with the application host and push field
checks complete. Both audited jobs total17,853 cases. Compact artifact, report and
job-log digests are included in the existing measurement. These shared iOS tests
do not claim execution of the separate four Swift Matrix SDK contracts.
