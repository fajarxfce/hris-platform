# Organization readiness control fanout

A verified first-key publication now has a bounded Matrix delivery use case. The
worker reads the existing MATRIX checkpoint, admits each current recipient and sends
the fixed opaque control to its actual original or fresh Matrix device. It never
creates keys, logs in or refreshes credentials. The PUSH checkpoint is independent.

## Ownership and progress

Claim finishes before recipient admission. Each recipient transaction acquires
account/shared, native, device and gateway guards before the exact queue checkpoint.
Current identity, confirmed public keys, hard native lifetime, client policy, group
eligibility, and original-device completion all remain required. Short interactive
access expiry is not retirement. The existing pure admission policy owns those rules.

Provider I/O runs outside SQL transactions, with the original event/device identity
and the shortest task/lease/admission/credential deadline. Both provider outcomes
reacquire current recipient authority. Only an unchanged admitted credential can
acknowledge successful delivery; a revoked recipient is suppressed. Actual cursor
advance and final directory/time checks commit together under V140 recipient scope.
Provider rotation itself takes the device guard and cannot commit inside this window.

Deferral leaves recipient scope before a separate publisher-only release. Failed
Result, cancellation, interruption or lost full checkpoint rolls back local progress.
No hidden actor switch or nested transaction owner is used. Retried controls retain
the same event/device coordinates so the existing transport reuses its transaction ID.

## Bounds

Claims are one to four publications; a page is at most eight strictly ordered targets.
The use case stops starting work after its 20-second budget or earlier 60-second lease
expiry; provider calls receive the remaining deadline. Existing SQL statement/lock
timeouts bound a statement already in progress. Worker cancellation wiring is separate.
The publication lasts 24 hours. No-progress retries stop after eight claims, with
exponential delays and provider backoff capped at 300 seconds. Successful cursor
progress resets attempts. Expired credentials defer without any acquisition loop.

## Validation

The final gate passes 221 tests in 215.181 seconds: 205 domain, six existing real queue cases
and ten new use-case cases using ordinary worker-role PostgreSQL repositories. Only
external transport is fake; real transactions, RLS, encrypted provider metadata,
actual gateway bootstrap and queue migrations remain in the test. Cases cover
independent PUSH state, expired short access, exact controls, finite backoff, expired
provider credentials, changed generations, post-I/O revocation, failed/cancelled
post-write rollback, final eligibility loss, deadlines, stale/channel/page rejection
and a blocked competing provider writer. There are zero failures, errors or skips; format and architecture pass. All 8,725
source inputs and every fresh XML report are hashed. The first gate's invalid expired-token fixture is retained.

[Measurement](../measurements/collaboration-space-readiness-control-fanout-2026-11-09.json).
Actual Synapse transport/replay proof remains in the accepted
[protocol](space-readiness-control.md); this increment does not repeat it.

## Next integration

No shared DI, scheduled worker, Mobile consumer, native SDK, phone or live rollout
is activated here. Integrate the independent push wake, wire both bounded worker
owners and validate full API plus unfiltered worker tests, then implement durable
Mobile foreground/background ownership before product routing. All 54 communications
acceptance groups remain open.
