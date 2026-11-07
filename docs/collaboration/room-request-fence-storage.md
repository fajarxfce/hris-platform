# Retained non-execution storage for room requests

V138 adds immutable, account-owned evidence for a stable v2 room operation that can
no longer execute. The existing `OperationRepository` and `MutationReceipt` contract
remain unchanged. A fence has its own ID, timestamp, full stable request descriptor
and payload fingerprint; it is never encoded as a fake successful receipt.

The primary key is account plus original operation. There is no expiry or deletion
policy, and no foreign key to the original native parent or device: their removal
must not make a delayed original executable again. The canonical conversation still
owns the immutable pair. Exact global actor RLS protects reads and inserts; the future
owning endpoint must separately require current native, group and pair admission.

## Serialization and rollback

Fence and stable receipt insertion acquire the same original PostgreSQL advisory
transaction lock used by `OperationReceiptDataSource`. Its key is the literal
`null:<account>:collaboration.space_room_setup.v2:<operation>` for global scope.
Current callers must acquire it before room, account and native locks. Database
triggers retain the exclusion even when a queue mutation precedes receipt insertion
inside one transaction: a fenced receipt fails, so the whole transaction rolls back.

A committed stable receipt prevents fencing. A committed fence prevents every stable
payload under that original account/operation. Stable receipts and fences cannot be
updated or deleted. A different legacy v1 namespace remains independent, including
when it uses the same UUID. Existing receipt bytes are preserved by the migration.
No provider operation, existing queue cancellation or successor is created here.

The schema validates owner, descriptor bounds, optional observed-account/revision
coherence, canonical pair and finite nonfuture timestamps. It does not require an old
device to remain usable. The repository integration must verify the stored fingerprint
against the existing stable descriptor hashing contract; runtime dispatch must return
a typed fenced outcome after current admission, before attempting a write.

## Verification and next integration

Seven actual PostgreSQL migration cases pass: upgrade/retained receipt preservation,
actor/company/anonymous and worker read isolation, immutable history, malformed
descriptors and times, cancelled/failed transactions, competing receipt/fence commits
and rollbacks under the exact advisory lock, parent removal and `pg_dump`/`pg_restore`.

The first gate passed 24 cases in 315.431 seconds, but its 17 existing room-writer cases
still used schema 133. Their fixture was explicitly upgraded to 138; the follow-up
passed all 17 cases in 213.004 seconds with the new triggers. Only that fixture target
changed between gates. Both used 8,626 unchanged inputs within their respective runs,
with zero failures, errors or skips, and formatting and architecture passed. The first
gate generated and compiled the new jOOQ bindings. There is no shared DI change.

Recovery tooling now accepts schema 138 with exactly the same encrypted envelope
projection as 137. Its 26 cases pass; the initial future-schema expectation failure
and its correction to 139 are retained.
[Source hashes and exact runs](../measurements/collaboration-room-request-fence-storage-2026-11-07.json).

[Repository mapping and current native owning fence admission](native-room-request-fence.md)
are now validated with API/FULL worker integration. Typed HTTP, Mobile durable
fence/successor linkage and room projection remain required. A null
receipt, timeout or denial is still uncertain. This migration is not deployed; live
schema stays 116.1 and organization routing stays disabled. It does not establish
physical-phone message latency or change the original SDK keys/message IDs.
