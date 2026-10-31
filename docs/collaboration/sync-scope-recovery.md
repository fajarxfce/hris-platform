# Classic sync scope recovery

The pinned Synapse 1.164.1 provider experiment uses two rooms which already exist
before the first client sync, then expands the permitted room filter after the
provider cursor has advanced. This models publication of an HRIS room binding
after its provider events. It is a provider-only topology, not permission to
create two native canonical direct intents for one pair.

The actual checks establish:

- `room.rooms` filters joined rooms but does not exclude the other invited room
  from initial `rooms.invite`. HRIS must independently filter every room bucket;
  a provider filter is not an authorization boundary.
- An ordinary incremental sync does not recover an older newly included room.
  `full_state=true` recovers joined-room state, but does not replay its old
  invitation while `since` is supplied. Initial acquisition without `since`
  recovers that invitation.
- In the invited-history fixture, the joined creator's `m.room.create` is absent
  from both full-state sync and a repeated initial sync. An explicit current
  `GET /rooms/{roomId}/state` returns the actual creation event with version 12,
  expected creator and room ID. The gateway must obtain required immutable
  creation metadata through admitted reads, rather than inventing an event.
- State is interpreted in order: state before timeline, then state events in the
  timeline. Do not merge arbitrary mutable current state ahead of an older
  timeline. The immutable creation event has a separate safe identity check.

The pinned [sync implementation](https://github.com/element-hq/synapse/blob/v1.164.1/synapse/handlers/sync.py)
also immediately returns when `full_state` is true. Forcing it on every request
would bypass normal long polling. Reacquire when the admitted scope changes,
then resume ordinary polling; caller-requested full state needs an explicit
bounded policy.

For the first native direct scope, the retained conversation and published room
binding are immutable and undeletable (schemas 110/111). Current caller admission
and retained history are separate from the peer's eligibility for new sends.
Do not discard acquired history because another participant's short access/MFA
expired or mechanically apply future-recipient rules to historical reads. Groups,
resource contexts and removal have different scope/version requirements.

The next implementation must own a bounded current room projection, a cursor
bound to account/company/device, scope-change recovery, and a separate cancellable
long-poll resource owner. It must not acknowledge a provider to-device cursor
which the admitted client has never received. Only current native admission can
publish a response. No public sync endpoint is opened by this experiment.

[Evidence](../measurements/collaboration-sync-scope-2026-10-31.json) retains the
corrected assumptions, focused checks and source hash. Complete pinned SDK
integration, encrypted process/store recovery and capacity remain separate gates.
