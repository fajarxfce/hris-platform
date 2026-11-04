# Organization room storage

V131–V133 separate immutable conversation intent, participant-owned preparation
requests and one verified room binding. The conversation creator, initial requester,
actual Matrix creator and publishing participant can differ. The initial intent is
provenance, not permission to impersonate an offline participant.

A LEGACY intent pins the exact original company, conversation, room, binding time and
creator. A SPACE intent pins both canonical users and a deterministic group alias.
[V132 routing](original-pair-routing.md) prevents same-original-company participants
from splitting original and group history, including concurrent fixed snapshots.
An existing unbound original conversation still requires explicit original recovery.

Each of the two exact participants may retain one private request head, keyed by
space, conversation and account. A partial unique index permits only one QUEUED or
RUNNING head per conversation. The other participant can resume after a terminal
outcome with its own current native/device/provider proof; it does not replace or
read the first participant's proof. Each head retains its own revision and previous
outcome. The public attempt identity is **account plus revision**.

Requests last at most five minutes in storage; native admission currently narrows
them to at most sixty seconds. Each revision permits at most eight attempts, leases
at most sixty seconds, a claim at most eight rows and at most sixteen active worker
leases globally. Claim and expiry joins include the exact account key. The claim
transaction commits before application account/native/device guards are acquired.
No repository creates a transaction, silently refreshes authority or retries forever.

V134 adds `ROOM_PREPARATION` to the existing finite provider request pool. It shares
slots 1–7 with gateway requests while slot 0 stays reserved for renewal. Room control
uses its own current native/device coordinates without a gateway ID or version.
Expired slots may change purpose only with the same owner and a new token; exact
release cannot remove a successor. Lifetime is rechecked after a device-guard wait.
No credential, key, RLS or encrypted recovery purpose changes. See the
[capacity evidence](../measurements/collaboration-space-room-request-slots-2026-11-04.json).
The bounded room worker still owns admission, provider I/O and atomic publication.

Account-only RLS protects full private heads. The restricted non-login status-view
owner has column privileges for safe operational metadata, including public account
and revision, but none for native/device/provider proof. The view selects the
successful publisher, else current active work, else the latest terminal request.
Retained metadata survives parent purge; current HTTP admission is still required.

Publication uses the exact current publisher's queue revision and lease, current
native/security/device/provider proof and both canonical participants. The binding
records the actual observed Matrix creator, which must be one of those participants.
LEGACY additionally requires the exact original creator and room. Binding, matching
request success and change journal commit atomically; deferred checks reject partial
publication. The one binding is immutable, even if an old worker returns late.

Upgrade retains all existing private heads and backfills existing bindings with their
previously pinned creator. The old first-requester-only writer may omit the new
creator field only when publishing as that original requester; a recovering peer
must explicitly provide the creator it verified. New typed writes always provide it.
Strict mappers reject malformed ownership, finite-time/state/outcome tuples and
readback. Failed results roll back; cancellation and interruption propagate.

See [native request/status](native-space-room.md) for current admission and HTTP
semantics. Room provider reconciliation, recipient content/sync/push and Mobile SDK
adoption remain separate work. Group chat stays disabled until that path is complete.
The accepted V131 foundation evidence is retained in its
[measurement](../measurements/collaboration-space-room-storage-2026-11-04.json);
[V133 evidence](../measurements/collaboration-space-room-peer-recovery-2026-11-04.json):
all103 database plus96 actual API cases PASS1120.114s, full128 worker
cases PASS375.998s with one existing opt-in skip, and all31 retained
publication cases PASS265.182s. Quality checks pass. Prior103
domain and typed5/native5 passing evidence carries only with the recorded source
continuity. Recovery19 passes, MAX133/unknown134. Initial fixture/compilation failures
are resolved and recorded; all test sessions closed. No room worker/SDK rollout claim.
