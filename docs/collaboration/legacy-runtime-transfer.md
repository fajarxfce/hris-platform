# Retained legacy runtime transfer

V123 adds storage for one immutable original company/device/account/native owner
and at most eight reusable in-flight request slots per device. Both tables start
empty. The schema does not freeze existing devices, move provider credentials,
change SDK directories, or grant group access. Old callers are not yet connected
to these fences; transfer remains unavailable until that integration is complete.

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

Application integration must cover `AccessNativeCollaborationMatrixDevice`,
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
an explicit cast; the corrected query passes. No DI or provider caller is wired yet.
