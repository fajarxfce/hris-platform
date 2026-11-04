# Organization push wake storage

V137 adds a separate coalesced wake queue keyed by organization space and group device.
Its account/native-parent identity references the exact V136 capability binding; no
organization ID is stored in a company column. Original company push storage is unchanged.
Both pending and published group capabilities can own hints: Matrix may accept a pusher
whose response was lost before publication. Storage ownership is never delivery authority.

Root account RLS permits only owned metadata with no selected HR company. The worker role
may claim and update bounded delivery state, but must separately admit the current
account/group/native/device/gateway/registration before sending. Native-parent cleanup
retains queue/capability metadata. Temporary authorization loss cannot erase recovery
identity; current callback/worker admission is still required integration.

A device retains one head, a finite revision and at most 64 recent event hashes. Duplicate
recent events and unclaimed quiet hints coalesce. Older-than-window events may wake again;
Matrix/local event identity must deduplicate presentation. Hint lifetime is at most 86,400
elapsed seconds, including database sessions using a daylight-saving timezone. Retry
attempts are bounded per revision. A new hint can advance exhausted work without resetting
its immutable ownership or losing a concurrent lease.

Group claims admit at most eight active leases globally, batches of at most eight, lease
lifetimes of at most 60 seconds and at most eight attempts per revision. The original
company queue retains its separate capacity of sixteen. Exact lease identity fences every
finish/defer. An old acknowledgment consumes only its captured revision, leaving newer
hints pending; an expired or replaced lease cannot complete its successor. Deferring an
older revision leaves newer work immediately available. A shorter lifetime on a later
hint may legitimately precede a retained lease expiry; delivery must bound both times.

Typed mapping validates owner coordinates, counters, visibility, times, complete lease
fields and bounded unique hashes. Acquisition and mapping share the safe database boundary.
Append/finish/defer verify durable readback inside the caller's transaction. Missing,
foreign or malformed readback fails and rolls back the actual write. Cancellation and
interruption propagate. Queue acknowledgments never supply chat delivered/read facts.

Migration, RLS, parallel callbacks, finite retries/capacity, stale leases, timestamp bounds,
failed readback rollback, parent purge and actual PostgreSQL archive/restore are exercised
with ordinary runtime/worker roles. The isolated restore retains the pending revision,
lease and encrypted capability; finishing restored work leaves the original database
untouched. This is storage/recovery proof, not actual FCM or client ownership acceptance.

Recovery accepts schema137 without changing the schema136 encrypted-envelope projection;
unknown future schema138 remains rejected. The new queue contains only opaque wake
metadata. Current callback admission, mixed company/group handling, worker delivery and
Mobile background/reply routing remain unfinished. Group rollout stays disabled and all
54 communications PRD groups remain open.

See [accepted gate evidence](../measurements/collaboration-space-push-queue-2026-11-04.json).
