# Native SDK direct-conversation metadata

The gateway already publishes `m.direct` in every sync response from the captured
HRIS DIRECT room scope. The pinned Rust SDK may nevertheless issue a PUT after
joining an invitation: `finish_join_room` calls `set_is_direct(true)`. The previous
security surface rejected this request even after the room join succeeded.
Test tracing now runs before the security filter, recording only bounded methods,
static route segments and status codes; run `92c1310f` confirms this exact 403.

GET `/_matrix/client/v3/user/{userId}/account_data/m.direct` returns the same
HRIS-derived map as sync. PUT on that exact path confirms an already-current
value: it succeeds only when the detached, normalized request equals the current
owned projection. It creates no metadata registry, changes no membership or room
kind, and forwards no account-data write to Synapse. This is an idempotent
confirmation of the existing value. An attempted addition, omission or reassignment
returns 409 `collaboration_direct_metadata_managed`; a changed scope during the
operation returns `collaboration_direct_metadata_changed`.

Both methods require the exact Matrix identity derived from the calling gateway
session, current native parent, company membership, chat permission and client
policy. A different subject returns 403. They reuse native protocol admission and
recheck ownership after pending acquisition, including private credential renewal.
Current denial takes precedence. The complete append-only DIRECT scope remains
independent of a former peer's current eligibility to receive new encryption keys.

Only this account-data type is supported. Other account-data, profile and arbitrary
provider operations remain denied. GET rejects request bodies and extra query
parameters. PUT requires a single bearer and JSON body, disallows duplicate keys,
trailing content and unexpected encoding, and bounds reading to 1 MiB/two seconds.
Domain validation bounds the map and total room references to the existing 10,000
room scope limit; IDs are bounded ASCII, room lists are nonempty and duplicate-free.
Room order is normalized, and input lists are copied before pending work.

This preserves the existing sync filter and signed provider cursor. It does not
reset synchronization or introduce another transport request on each sync. Whole
scope limits are defensive bounds, not a 5,000-employee capacity measurement.

[Verification](../measurements/collaboration-sync-recovery-2026-11-01.json)
records 40 domain, 204 data and 20 HTTP cases, plus actual SDK run `c17b1b10`
(39.126s) with successful post-join metadata confirmation, process recovery,
retained-cursor retry and encrypted replies in both directions.
