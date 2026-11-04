# Organization callback and worker integration

The private Matrix callback accepts one fixed event-only application profile with typed
company (`hriscp1_`) and organization (`hrisgp1_`) capabilities. Both share the existing
8,192-byte body bound, 32-device total limit and four concurrent callback slots without
a waiting queue. Duplicate fields/keys, arbitrary content/device data and unknown
versions are rejected before dispatch. The original ten-second executor deadline is
passed unchanged to the independent company and organization use cases.

The delivery adapter validates the complete batch and verifies that each handler's
rejected keys are unique members of that exact input scope. It returns explicit unknown
or permanently retired capabilities in original input order. Partial committed work is
idempotently retryable. Temporarily disabling organization rollout preserves group
keys while company handling continues. Global push/provider disablement remains
unavailable; a lookup failure must never be interpreted as permanent retirement.

The group worker uses the same bounded lifecycle implementation with a separate typed
lease and separate task/timer pools. Its four local tasks have no waiting queue; group
PostgreSQL capacity remains eight across workers, distinct from the company capacity
of sixteen. Claims are finite, have at most eight attempts per revision and leases of
at most 60 seconds. Every group worker requires the existing push/delivery/FCM/provider
flags plus organization enablement. Group disablement alone preserves the company
worker. Both pool pairs are drained/stopped by their application owner.

Before delivery, current group admission is reacquired under account/native/device/
gateway/binding guards, and the exact captured queue lease is locked. The native push
token is acquired only for the currently admitted registration version. Current target
and directory checks occur again before external FCM I/O. Delivery has one original
20-second budget bounded by the lease, head lifetime and current target deadline.

The fixed schema1 `collaboration.space_chat_available` envelope carries `spaceId`, not
`companyId`, plus opaque account/native/device/revision/timing metadata. A currently
owned visible event may add Matrix room/event locators; no message content or receipt
status is included. Retention is at most 24 hours, independent of dispatch budget.
A newer visible revision cannot be attributed to an older lease. The shared FCM mapper
keeps Android data-only; local admission/decryption controls display. Apple fallback
and full notification extension behavior still need their separate client acceptance.

After either cloud outcome the current target is reacquired. Native, gateway, device,
provider or registration changes defer old results without disabling a rotated token.
A current UNREGISTERED result atomically acknowledges only its captured revision and
disables the exact registration, verifies disabled readback, and checks current group
eligibility/time before commit. Failure rolls back both writes. New callback revisions
survive old acknowledgments. Temporarily suppressed targets and retryable provider
failures defer within finite attempts; permanently retired or expired work completes
without acquiring a token. A stale/expired lease cannot acknowledge its successor.
Cloud acceptance/queue progress is never chat delivered/read status.

See [accepted frozen-source evidence](../measurements/collaboration-space-push-delivery-2026-11-04.json) for the domain/delivery/PostgreSQL, API and FULL worker gates. Tests inject cloud outcomes and exercise
real encrypted registration and PostgreSQL transactions. They do not claim actual
FCM delivery to the new group Mobile owner. The existing normal phone build and live
rollout remain unchanged; organization rollout stays disabled and all 54 PRD groups
remain open. Mobile group routing, original SDK store adoption/queued-key decryption,
native hard-parent recovery and one visible notification across simultaneous old/new
pushers remain mandatory before product rollout.
