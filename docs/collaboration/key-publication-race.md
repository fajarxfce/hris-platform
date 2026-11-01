# Native device-key publication and foreground history

An SDK can receive Matrix's new-device notification before HRIS records the
publisher's final readback. The previous recipient projection omitted that device
during this interval. A successful empty key query could therefore leave the peer
with a stale device list and encrypted messages it could not decrypt.

Schema 114 includes the immutable reservation, which commits before provider
upload, and exposes a `publication_confirmed` flag to the internal repository.
Only public-key discovery includes these candidates. Every returned provider
identity still requires the exact user/device, canonical self signature and pinned
fingerprint. An unuploaded reservation may be absent; a confirmed key may not.
A publication transition during a query invalidates its captured result and
requires a retry. Querying never promotes the publication record.

Room messages, one-time-key claims and encrypted to-device submissions retain
their confirmed-publication requirement. Discovering a key early does not grant
these operations. Company/direct-pair scope, account and membership authority,
hard native-parent eligibility, current caller admission and final revocation
checks remain mandatory. Schema 114 adds no secret envelope; recovery accepts it
and rejects the unknown next schema. The restricted view owner and grants survive
the tested upgrade and actual dump/restore.

## Native evidence

The direct-worker fixture now seeds 32 encrypted peer messages after its original
queued command has survived process death, been sent, stored and retired. It then
resolves the actual application's `ChatThreadViewModel`. The oldest peer original
must initially be absent from canonical storage, become available through older
history requests, and remain projected after the screen connection stops.

The server independently verifies all 33 provider events: one worker send and 32
distinct peer sends, their expected senders, encrypted event type and ciphertext.
The successful normal-flow run used four history actions and 43 provider
references. Ten state-only references remained; no message reference remained
unresolved. Application admission counts were 30 device, 28 conversation and 30
room reads, with zero failures. Cleanup completed.

The same Android APK (`d685231d…`) failed before the server fix with a connected
thread and only one saved original, then passed against the corrected server in
94 seconds. A separate diagnostic experiment forced a peer key query and passed;
that intervention was removed from the final fixture. The measurement retains
both failed native attempts, that experiment, build corrections and regression
results. The test-only Synapse message burst is 64 for the 32-message seed; other
fixtures keep the default 10. This is not a production capacity measurement.

Fixture shutdown follows the existing bounded runtime close contract. It drains
pending close results, attempts every cleanup operation and preserves the original
failure if cleanup also fails. It does not change product shutdown deadlines.

[Measurements](../measurements/chat-foreground-native-2026-11-01.json) bind the
Mobile and Platform sources and distinguish direct worker, SDK recovery and actual
Android WorkManager execution. These fixtures accept this bounded Android
composition, not a complete chat release, production deployment, Apple/web runtime
or complete PRD group. Continue bounded local browsing, durable drafts and safe
pending cancellation, incoming push/replies, richer messages, key continuity and
backup, then calls and meetings.
