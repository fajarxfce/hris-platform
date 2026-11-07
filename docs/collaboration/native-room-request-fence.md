# Native recovery of original room requests

The owning room-request fence use case resolves one original stable v2 operation
under current native, organization and participant admission. It returns either
the original setup receipt or retained non-execution evidence. An absent receipt
alone remains uncertain. Neither result acknowledges an SDK message, makes a room
ready, nor authorizes message delivery or a successor operation.

`FenceNativeCollaborationSpaceRoomRequest` owns the transaction and coordinates
repositories. It takes the same original operation lock as the stable writer,
then the room, sorted account and current native-session locks. It checks current
client policy and both participants before disclosing a retained result or payload
conflict, and rechecks admission and the original 30-second budget before completion.
Clock rollback, late denial, failed journal writes, cancellation and interruption
roll back new evidence, audit and outbox together.

The original native parent, logical device and provider do not need to remain
available to recover or fence the original request. They are not impersonated by
current authentication. A still-admitted stable writer separately requires its
current device/provider and checks the retained fence before queuing or recording
any result; it returns `collaboration_room_request_fenced`. Legacy v1 operations
keep their separate namespace, including when they share the original UUID.

A dedicated raw datasource reads/writes the V138 row. The repository maps values
and errors only; its mapper verifies the complete stable fingerprint, account and
operation, descriptor coherence and microsecond timestamp. The use case alone
owns admission, final checks and journal publication. Existing operation receipts
are not repurposed as fake non-execution results.

Exact replay returns the same fence ID and timestamp. Evidence survives original
parent purge and supports a newly authenticated parent for the same account.
Another account cannot recover the owner's evidence; a legitimate participant's
own original-operation namespace is independent. Changed request contents conflict
only after current admission. The V138 database exclusion and immutable storage
remain the final protection against receipt/fence coexistence.

## Validation and remaining integration

The focused run passed 195 cases in 319.052 seconds with 8,635 unchanged inputs:
164 domain cases, 27 actual PostgreSQL native-room cases and four mapper/repository
cases, zero failures/errors/skips. Formatting and architecture passed. Four ordered
writer/fence commit and rollback races exercise real advisory locking, and the
transaction cases cover exact replay, existing receipts, removed original parents,
missing original devices, current denial, invalid scope and late failure rollback.
The mapper cases include technical cancellation/interruption and corrupt records.

The existing native API, disabled API, OpenAPI and FULL worker gate passed 241
cases in 540.839 seconds on the same 8,635 unchanged inputs, with zero failures
or errors and one existing opt-in elapsed-retention skip. Formatting and architecture
passed again. [Exact sources and reports](../measurements/collaboration-room-request-fence-native-2026-11-07.json). HTTP transport, durable Mobile evidence/successor linkage,
room projection and product routing are separate next steps. The group feature
remains disabled and schema 138 is not deployed; live schema is 116.1. Physical-phone
send latency remains unresolved. Original SDK keys and message IDs are unchanged.
