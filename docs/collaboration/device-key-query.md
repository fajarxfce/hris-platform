# Native Matrix recipient key query

`POST /_matrix/client/v3/keys/query` accepts an opaque native HRIS gateway bearer.
Native access credentials, provider credentials, cookies and URL credentials do
not substitute for it. The shared key controller preserves the upload endpoint's
strict JSON parser, duplicate/trailing-input rejection, 64-KiB request limit,
two-second streamed-read bound and no-store responses.

The request contains `device_keys` and an optional integral `timeout` from zero
to 30,000 milliseconds. This is an advisory client field; internal provider
query timeout remains 1,000 milliseconds within the five-second transport and
captured admission deadline. Explicit null, unknown fields and empty user maps
are rejected. At most 100 canonical users and five distinct device IDs per user
are accepted. User names must identify the selected company and configured
Matrix server; alternate spellings cannot cause foreign account lookups.

An empty device list means all currently eligible HRIS devices of that account.
It never becomes a provider wildcard. The use case selects confirmed published
devices through the [recipient projection](recipient-devices.md), after checking
current account/membership/CHAT permission and an existing direct pair, or self.
Requested unknown, unrelated or unavailable accounts produce empty key entries
without querying their provider devices. This permits SDK batches containing an
old peer to update current recipients without exposing inaccessible key material.
Caller authentication, company/client policy and MFA denial still reject the request.

The private admission runner acquires policy/company/member guards, then all
involved account guards in Java UUID order, then the caller's native parent,
device and gateway head. This does not change PostgreSQL's separate canonical
direct-pair ordering. No SQL transaction spans provider I/O. Private bearer
renewal keeps its existing generation fence and never logs out a losing token.

Before and after a recipient-key exchange, admission and the selected device set
are acquired again. Newly eligible devices, removals, security changes or changed
hard deadlines invalidate the captured result and require a fresh query. An
unavailable peer then yields an empty entry on that fresh query. Current caller
denial takes precedence over a provider error. Requests with no eligible device
still perform the final current check and make no provider key-query request.

The provider adapter permits only the exact requested user/device collections.
It drops unsigned device labels and other users' signatures. Every returned
identity must have the expected user/device, valid canonical Ed25519 self
signature and the retained fingerprint. A confirmed device missing from the
provider result is an invalid response; it is not silently omitted. A replacement
identity returns `collaboration_device_key_recovery_required`, without clearing
client crypto state or overwriting the stored fingerprint.

The recipient query has a one-MiB response bound for up to 500 selected devices.
Other control responses retain 64 KiB. Fixed/chunked success and error responses
share their bounded subscriber, cancellation and 32 immediate transport permits;
there is no unbounded wait queue. Input remains 64 KiB. Bounds are not a capacity
claim for a 5,000-employee directory or large group.

Only validated public keys and an empty `failures` object are returned. This is
native self/direct key discovery. [Signed key claim](device-key-claim.md) uses the
same current recipient boundary. To-device envelopes, scoped sync,
room contents, browser-parent admission, group/context membership, cross-signing
and backup retain their own gates. Actual SDK traffic through the complete HRIS
gateway is not established by the component-level provider/HTTP tests. A client
with no eligible peer device must keep its message pending rather than reporting
a self-only ciphertext as delivered.
