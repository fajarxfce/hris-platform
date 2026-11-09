# Organization readiness over the existing Matrix connection

A committed recipient key can make an existing original queue deliverable while its
sender is already connected. The organization protocol uses the fixed clear to-device
type `dev.fajar.hris.space_recipient_ready`, version1. Its nine canonical string fields
are schema_version, event_id, space_id, account_id, native_session_id, device_id,
published_at, expires_at and publication_revision. No message, private key, peer
identity or credential appears in this hint. It grants no send or content authority.

Revisions are positive signed64-bit values, preserved as decimal strings through
Long.MAX_VALUE. IDs are canonical nonzero UUIDs. Publication time has a finite range
and expiry is exactly one day later. Unknown fields, company coordinates, noncanonical
numbers and unsupported versions are rejected. The publication queue must supply
commit-ordered revisions; protocol validation alone does not establish commit order.

The provider repository receives the exact already-admitted logical owner and its
physical Matrix identity. An original HRIS_ device stays original; a new HRISG_ device
may retain its original company Matrix user. The mapping never derives a new provider
identity from the space UUID. One deterministic event/device transaction is sent to
that user's own device, using its existing credential and the supplied deadline.
Raw HTTP lives in the datasource, with4KiB request/response bounds. Invalid provider
acknowledgements remain failed/uncertain; cancellation and interruption propagate.
There is no provider login, key upload, token refresh or retry loop in this adapter.

Shared sync framing declares either company or organization ownership. Each accepts
only its own fixed event type from the syncing user's own identity, for its exact
logical scope/account/device. A malformed or foreign control is discarded while
valid encrypted to-device frames and the sync position survive. Company V2 controls
cannot cross into adopted group sync; group V1 cannot cross into company sync.
Native-parent admission, expiry and durable deduplication still belong to the consumer.
Both public gateway scopes continue to reject clear readiness send routes.

The actual Synapse test uses a retained original user/device under its logical group.
It wakes an existing sync, preserves an unacknowledged event across replay, and keeps
the transaction stable after a same-device bearer change. Provider restart can replay
the control, so clients must retain/coalesce a wake before acknowledging its revision.
A signal is never a message ACK; existing original delivery still needs current
admission and actual provider evidence before projecting Sent.

[Audited validation](../measurements/collaboration-space-readiness-control-2026-11-09.json)
passes216 cases in119.967 seconds:196 domain,14 provider/projection and6 native API.
There are zero failures, errors or skips. Both new group and existing company actual
Synapse tests pass. The archive verifies every fresh XML and all8,706 stable inputs;
format and architecture pass. No shared DI constructor changed, and the previous
full worker gate is not counted again.

Next implement the transactional publication counter/checkpoints and bounded current
target fanout, then the Mobile foreground consumer, durable receipt and background
owner. Those are not implemented by this protocol increment. No Mobile/SDK/schema,
live runtime or phone build changes; group routing remains OFF and all54 PRD
acceptance groups remain open.
