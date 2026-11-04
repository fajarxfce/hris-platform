# Current native access to original room history

An active group gateway device can now read an owned original company room before
that room has a published group binding. Messages, event context and single-event
routes retain the current native/session/device/provider admission path. The original
Matrix user/device and original room metadata remain explicit; group IDs never replace
company IDs and no lease, request or publication metadata is fabricated.

## Lookup and current authority

The use case builds canonical history coordinates only after its current admission
observation. It first acquires the exact participant-owned group binding. A successful
binding must pass the existing group metadata and own-user checks. Only a successful
absent result may proceed to original-room lookup; acquisition errors or malformed
published data cannot invoke the fallback.

Original lookup uses the company encoded in the exact canonical original Matrix user,
not the selected HR company. It queries the bounded V129/V130 original room projection
with explicit space/account/company/server/room coordinates. The pure original snapshot
checks ownership, participant order, actual creator, immutable version and stored time
ordering against the current clock. A fresh HRISG_ device on an original canonical user
and a completed retained HRIS_ device are both eligible under current native admission.
Only the exact retained device retains original company-cursor bridging.

The prepared original or published metadata is pinned around both provider outcomes.
A metadata change, adoption during the request, lookup failure or current revocation
withholds the response. Original read cursors remain valid across an explicit retry
following adoption because the protocol uses the same seven group coordinates.
Provider I/O remains outside SQL, inside the original finite deadline and owned request
lease. Cancellation/interruption retain cleanup. No current receiving peer or new
own-key publication is required to retain history. Sending keeps its separate current
recipient and published-room requirements.

## Validation

See [accepted gate evidence](../measurements/collaboration-space-original-history-access-2026-11-04.json).

Two new native/PostgreSQL cases exercise fresh and retained original identities through
registration, activation, bootstrap and all three reads without a group room binding.
The peer has not migrated. Original conversation and binding creators differ. Peer
permission revocation and native-parent purge leave owned history readable. A failed
group lookup never invokes original acquisition. An injected late metadata change,
provider cancellation and native revocation on both successful/failed provider outcomes
withhold responses and clean leases. Original account metadata remains unchanged.

The existing original room seeding helper is shared with the retained room publication
case; the full publication suite validates its existing adoption/recovery behavior.
Existing native group history tests still cover scope, invalid inputs, current authority
and deadline behavior. API and FULL worker contexts validate the added existing
conversation repository dependency in shared DI.

## Remaining integration

Complete group sync still needs the merged original/published inventory, separate sync
cursor handling and fixed provider projection. Actual SDK acceptance must preserve
original crypto stores and authenticate/decrypt queued peer keys before adoption.
A pinned SDK source audit shows signed embedded sender_device_keys and retained stored
peer keys are both relevant; it is not decryption acceptance and does not justify new
send or OTK-claim authority for obsolete peers. Then metadata/push, Mobile SDK ownership
transition and native hard-parent recovery remain. Group rollout stays disabled and
all 54 PRD acceptance groups remain open.
