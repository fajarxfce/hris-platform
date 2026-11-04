# Retained legacy runtime transfer

V123 adds storage for one immutable original company/device/account/native owner
and at most eight reusable in-flight request slots per device. Both tables start
empty. The schema does not freeze existing devices, move provider credentials,
change SDK directories, or grant group access. All fixed legacy provider consumers and gateway issuance now fence original
runtime ownership. Admitted atomic transfer publication and client ownership
handover remain required before transfer can be exposed.

A runtime starts in LEGACY at version 0. A root-scoped operation can freeze it to
DRAINING at version 1, naming an exact PROVISIONING group device with matching
retained company/device and original account/native parent. That group's immutable
key reference is retained in the existing device registry. The freeze cannot be
undone, re-targeted, or deleted. No completed-transfer state is supplied yet: it
requires the separate admitted atomic group publication/legacy retirement command.
A public fingerprint alone does not prove local private keys or client shutdown.

Each legacy operation claims one of eight slots under the runtime row guard. The
claim checks its original company/account/native scope and the current bound
parent, account credential version, access/hard expiry and live device. It returns
one lease or no capacity; it does not retry. The lease lasts at most 60 seconds and
cannot exceed the caller's admitted deadline. Database time is checked again after
waiting for the owner row. A frozen owner accepts no further legacy claims.
Use cases must still validate exact native proof, company/client/MFA policy and
the fixed provider operation. Metadata and leases supply no provider/content grant.

Slots cannot be extended or replaced while live. Explicit release compares the
exact token and original ownership, and a reused slot receives a new token. Late
cleanup must not release its replacement. Expired or released slots are reusable
only under LEGACY ownership; the maximum stored row count remains eight. Cleanup
remains possible after freeze, offboarding or parent purge. Runtime provenance and
slots have no cascading native-parent foreign key. An expired lease allows bounded
server recovery; it does not demonstrate that a remote provider rolled back I/O.

Application integration must cover all fixed gateway operations,
`AdvanceNativeCollaborationProvisioning`, and `AdvanceNativeCollaborationDirectRoom`,
including bearer renewal and all final publication checks. Gateway bootstrap and
refresh must not recreate authority after freeze/transfer. Acquire ordinary
account/native guards before the runtime guard; commit before network I/O. Release
exact ownership in a finally path after the business transaction has completed or
rolled back. Never acquire an old job guard from a transfer command: old workers
already acquire the job guard before ordinary authority guards.

Completion must atomically retire the old device/provider/gateway and publish the
currently admitted group credential. Existing old credentials require a company
RLS context while group writes require root context; use a narrowly scoped reviewed
atomic port, not nested transaction owners or broad cross-company privileges.
Losing/late Matrix candidates must never be logged out because Synapse logout also
removes the winning same-device tokens and E2EE keys.

Before publication, Mobile must stop and join the old foreground/background SDK
runtime, queues and sync. Persist a recoverable explicit alias to the original
company/native directory and protected passphrase. Preserve pending transaction IDs,
room history, draft/read/push ownership and other legacy identities. Mobile commit
3d85d569 proves local public-key observation and clean-close/reopen continuity with
the actual Android SDK; it does not implement this handover. A different empty
store is correctly detected and must never become a replacement under the retained
device ID. Actual Apple SDK continuity remains open.

[Database evidence](../measurements/collaboration-legacy-runtime-fences-2026-11-04.json)
records five targeted cases passing in 79.963s and 113 full database/policy/storage
regression cases in 525.967s, with unchanged checked inputs and quality gates.
Fifteen recovery-tool tests pass after correcting the old unknown-future-version
assertion. Earlier compilation failures are recorded separately. No live schema,
product phone build, public transfer endpoint or group runtime is enabled here.

The typed repository now supports exact-owner initialization/read/row guards, a
bounded slot list, one claim, exact-token release and versioned freeze. Its raw
jOOQ source supplies explicit PostgreSQL timestamp typing; acquisition and mapping
share the safe database boundary. Repeating initialization returns an existing
freeze unchanged. Malformed or foreign rows fail mapping, and a reclaimed slot
cannot be released by an old token or altered ownership/timestamps.
[Port evidence](../measurements/collaboration-legacy-runtime-ports-2026-11-04.json)
records 26 passing mapping/actual-storage/native/worker cases in 115.242s with
8262 unchanged checked inputs and quality gates. Caller rollback, cancellation,
interruption and exact cleanup after native-parent purge pass. An earlier run
failed three actual-storage cases because the plain SQL timestamp argument required
an explicit cast; the corrected query passes. That port increment did not wire DI
or a provider caller.

Pure original-runtime admission and exact live-lease validation are also available.
They preserve current admission time bounds and distinguish a coherent DRAINING
owner from permanent account revocation. The repository can read/lock one exact
slot after the runtime head, so a publication transaction can serialize with its
finally cleanup. [Policy/guard evidence](../measurements/collaboration-legacy-runtime-policy-2026-11-04.json)
records 30 passing cases in 128.584s, including actual row contention against cleanup,
with 8264 unchanged inputs and quality gates. That policy increment did not yet
wire a provider caller.

## Accepted gateway integration

`AccessNativeCollaborationMatrixDevice` now captures one lease after its ordinary
current native/company/client/MFA admission and before preparation writes. A failed
preparation rolls back both its metadata and claim. One lease covers the entire
finite gateway operation, including internal provider credential renewal and all
key/pusher/read/write exchanges. Full capacity returns temporary busy without
provider I/O or an internal retry. Runtime then exact-slot guards are held only
inside short observation/publication transactions; network calls hold no SQL locks.

Each provider call checks current authority and the exact lease before and after
I/O, including provider failures. A freeze or replacement makes the late result
unusable. Credential/key/pusher publication checks the same guards, and the final
clock bounds include the lease deadline. The exchange deadline is the earliest
native admission, public access, internal provider credential or runtime expiry.
No losing Matrix credential is logged out; remote changes may already exist even
when their local publication is withheld.

Finally cleanup compares the original exact lease. A failed metadata result leaves
a finite recoverable slot and preserves the provider outcome without retrying it.
Original exceptions survive cleanup exceptions, including the same exception
instance. An already-interrupted thread keeps its flag and starts no cleanup SQL;
expiry enables later reclaim with a new token. These server facts do not prove a
client SDK stopped or a remote provider rolled back its request.

Full native data regression passes160 cases in243.562s, including actual Synapse,
freeze during renewal/key/pusher publication, eight-slot saturation, cancellation,
interruption, cleanup failure and real-time expiry/key retry. Actual API47 cases
pass152.165s; full worker126 active cases plus one existing opt-in skip pass342.882s.
Five actual Android SDK/worker cases pass1047.184s, including all three
176-message process-death recovery states, fresh same-identity ciphertext verification
and automatic WorkManager execution after network restore. All checked inputs remain
unchanged within each run; format/architecture pass and exact owned emulators stop.
[Gateway evidence](../measurements/collaboration-legacy-runtime-gateway-2026-11-04.json)
records the source relationship and all failed attempts, including the expired-token
verifier correction without relaxing production lifetimes. No group completion or
public transfer endpoint is exposed. Keep the remaining worker, issuance and Mobile
ownership requirements above.


## Remaining legacy consumers integrated

Bootstrap and refresh now hold the original runtime row through normal/replayed
credential publication and current admission. They claim no provider slot because
they perform no provider I/O. Refresh succeeds even with all eight provider slots
occupied. Failed initial issuance rolls runtime metadata back with credentials,
operation receipt and journal.

Native provisioning and direct-room workers retain their original job/company/
account/native guard order. They capture one bounded lease before network I/O,
check current authority and exact runtime/lease before and after success or failure,
and hold runtime then slot guards through final publication. All post-capture
paths release exact ownership in finally; interruption keeps its flag and finite
expiry permits recovery. A cleanup failure preserves original/same cancellation
without another provider attempt. No losing Matrix credential is logged out.

[Consumer evidence](../measurements/collaboration-legacy-runtime-consumers-2026-11-04.json)
records 19 focused, 166 full native, 47 API, 126 active full worker plus one existing
opt-in skip, and 5 actual Android SDK cases. All share 8264 unchanged inputs and pass
quality gates. Android gate takes 1035.238s and retains all three 176-message
process-death recovery states; its exact emulator is stopped. Earlier compilation
and old fixture-clock failures are retained, without weakening production deadlines.
This completes consumer fencing, not group runtime adoption.

The next native freeze command can initialize owned runtime metadata under root
scope: original device/binding SELECT policies are account-owned, and V123 explicitly
permits root initialization. It must still prove current group/native/client/MFA,
canonical user, exact original parent/device/fingerprint and PROVISIONING target.
Metadata authority grants no old company resource access. Freeze/replay and journal
must be atomic and cannot retarget or unfreeze an owner. Bounded drain observations
are not final publication authority; completion must check actual DB time/remaining
leases after waiting. Expiry does not establish remote rollback.

Completion must also handle retained registration replay explicitly: its current
continuity policy requires an ACTIVE/unretired original source even for an existing
group record. A legitimate completed mapping needs its own exact continuity proof
with current admission; arbitrary existing records cannot waive those checks.
Group ACTIVE status requires a SUCCEEDED provisioning head. Reserved key references
must receive actual provider-key readback before publication. Keep the atomic RLS
and durable Mobile SDK/room/push/read requirements above; no public handover is
implemented by this increment.

Preserve V121's existing terminal revision reservation: only REVOKED may reach
1000000. Current mappers and actual database tests already cover that boundary;
new transfer activation must retain room for later revocation.
