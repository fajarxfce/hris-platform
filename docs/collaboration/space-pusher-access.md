# Native organization managed pusher

The existing empty-body managed pusher endpoint dispatches an opaque organization
access token to group admission. The client cannot provide a push token, capability,
callback URL, application ID or Matrix target. Strict duplicate authorization, query,
body and alternate-route rejection remains in the shared HTTP boundary.

Both collaboration and push deployment flags must allow registration, along with
current group policy, canonical device identity, completed original handover where
applicable, native parent/client assurance, provider generation and gateway validity.
The use case retains the existing bounded request lease and fixed 30-second operation
budget. Account/native/device/gateway/request guards precede binding operations.

Under current admission, a metadata-only native registration read must match the
account, original parent and platform, remain enabled, have a usable version and
valid timestamps, and not be expired. The use case never requests or decrypts the
FCM/APNs token. A stable separate group capability is reserved and journaled before
provider I/O. An existing reservation or publication retains the same key and Matrix
user/device, including a completed original company device.

Matrix registration runs outside SQL with the smaller of the original admission
budget and captured native registration expiry. Both provider outcomes are followed
by current admission and exact registration/binding checks. Token rotation or any
other metadata change withholds acknowledgment; retry uses the retained capability.
Cancellation/interruption propagates while lease ownership is released or remains
bounded by expiry. No retry resets the budget.

After provider success, publication, audit and outbox changes commit atomically under
current guards. Exact readback and final checkpoints are mandatory. Only the same
reservation may advance from zero to one; an already published record must remain
exactly identical, including its publication timestamp. Concurrent requests may
acknowledge one shared publication, with one reservation and one publication journal.
Provider failure, revoked authority or failed journal/readback retains uncertain
reservation for recovery and does not manufacture a successful publication.

Both company and group pushers can coexist on one original Matrix device. The current
company callback does not inspect the completed legacy runtime, so completion alone
does not stop company hints. Group callback and Mobile ownership integration must
verify one visible notification without discarding queued original hints while the
local store transition remains pending.

This connects group native registration, storage, shared provider adapter and strict
API routing. It does not yet connect group callback admission/queue/worker, Mobile
background ownership or actual product FCM delivery. The group rollout stays disabled;
all 54 communications PRD groups remain open. Actual SDK queued-key decryption,
store ownership transition and native hard-parent recovery remain required.

See [accepted gate evidence](../measurements/collaboration-space-pusher-access-2026-11-04.json).
