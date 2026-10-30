# Leave read completion — 30 October 2026

Eight older readers checked session assurance before acquisition but did not retain
it through their final response boundary. A slow query could release acquired
policy, employment, request or evidence data after MFA expired. Content preparation
could also start an object read after its proof had expired.

The correction retains the guarded identity projection and checks the current
clock after policy catalog/history, effective types, owned employment discovery,
request lists/history and attachment metadata. Content checks both its final
preparation and its post-storage completion. Storage remains outside database
transactions. At most one MiB is acquired per part; the caller receives an owned
copy and the storage buffer is cleared on success, denial, integrity failure or
cancellation. No query, schema, dependency or transport change is introduced.

Ten actual baseline cases failed at `5b60aba3`: nine released data and one started
unnecessary storage. The first fixture run had seven actual failures and three
upload-lease setup failures caused by its old application clock. Correcting that
setup produced ten actual failures in 39 seconds. All ten passed after the fix
(48 seconds), including original/renewed reads, exact content, unchanged business
rows and cleared buffers.

Final local acceptance passed 437 API/OpenAPI and 65 Leave domain cases, with no
failure, error or skip. The Leave data test task has no test sources. Formatting,
architecture and both application packages passed in 822 seconds. Six actual API/PostgreSQL/
browser scenarios passed in 51.5 seconds including setup: policy history,
request creation, staged decisions/cancellation/withdrawal, balance corrections,
calendar occupancy and reporting. [Measurements](measurements/leave-read-assurance-2026-10-30.json)
retain every selected suite, browser outcome and package hash.

The complete Leave module is still under review. Three older commands need
completion/replay/input-ownership correction; delegated reads, available actions,
decisions and original receipts need current-scope review. The five initial Leave
rows remain unverified. This read increment does not close the full Platform/Mobile
goal. No private runtime or phone change occurred; no generic client port is due.
