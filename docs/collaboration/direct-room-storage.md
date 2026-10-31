# Retained direct-room identity and provisioning

Schema 111 adds a durable job pointer and a separate immutable Matrix room binding
for each canonical direct conversation. The repository has a conversation advisory
guard, bounded single-record reads, compare-and-set retry and publication. It does
not call providers or admit content; application use cases own those decisions.

The pointer records the first provisioning actor as provider creator, its current
registered device, job and version. It must reference an active device bound to
the native parent named in the queued job. Job company, actor, conversation,
device, platform, fixed total and creation time must agree. Both participants may
read retained metadata in their selected company. Only the creator can insert or
replace the pointer, and it must belong to their immutable direct pair. No
security-definer function or bypass-RLS role is added.

A retry advances exactly one version and replaces a terminal failed/cancelled job.
It preserves creator, conversation and original creation time. The same creator
may use a different admitted device; ordinary native-parent cleanup must not
remove the pointer or change Matrix room identity. The canonical alias still names
the same creator's candidate room. A peer cannot take over that creator's private
credential or silently adopt an unverified room. If the creator cannot currently
resume setup, an explicit pending/failure state is required; this storage increment
does not implement an account-recovery or creator-transfer workflow.

Publication requires the current pointer's creator, job and generation, with that
job running and no progress committed yet. Its provider server/room ID is unique
and cannot be updated or deleted, even by a privileged direct writer. A published
binding prevents another setup retry. The use case must inspect the provider's
creation marker, creator, encryption and complete room security state before
publication, then recheck current HRIS admission and the job lease. The binding,
job progress/completion and journal must commit together. A database row alone is
not proof of those external checks.

Worker lock order is lease, room, company policy/company/membership, ordered
accounts, native parent and device. HTTP mutation takes its operation then room
before the same authority guards. Terminal-job lookup during retry must be a
nonlocking read; taking a job lock after room/access locks reverses worker order.
Provider I/O runs outside all SQL transactions. Single-record reads deliberately
avoid FOR SHARE, because that also applies UPDATE RLS and could hide immutable
metadata from the peer. Mutations require the owned room guard instead.

Versions retire at 999999; requests and provider identity fields remain bounded.
The existing queue owns finite attempts, company/global capacity and cancellation.
The new job kind has its own dashboard label; adding its enum value does not start
a worker handler. Recovery supports schema 111 without another secret envelope
format. [Native command/status and worker reconciliation](direct-room-provisioning.md) now
use these ports; their evidence is separate from storage. Content admission remains
a subsequent implementation step.

[Validation evidence](../measurements/collaboration-direct-room-storage-2026-10-31.json)
retains the six PostgreSQL cases, actual 110-to-111 upgrade/dump/restore and complete
API/worker selection, including corrected test compilation and the existing opt-in skip.
