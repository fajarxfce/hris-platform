# Organization gateway self access

Group gateway access currently exposes two fixed self operations: Matrix `whoami`
and `capabilities`. Identity/capability controllers dispatch only correctly shaped
`hris_mxg_a_` credentials to group authority. Existing company tokens keep their
separate path. Shared strict header/query/body parsing still applies. The group
feature remains disabled pending recipient/room authority and Mobile integration.
Fixed [own-key upload](space-key-exchange.md) now uses the same private admission
runner, with transaction-owned reservation and per-provider-step checks.

The self operations grant no sync, room membership, key query/claim, media, send,
read receipt or push authority. Each later operation needs its own scoped domain
rules before receiving group token dispatch. In particular, the canonical Matrix
user and organization directory never authorize every room visible to a provider.

## Admission and request ownership

Anonymous root lookup identifies the opaque credential's owner; it grants no access.
The use case then locks the real account, original native parent, bound ACTIVE group
device and exact gateway head/version. It reconstructs the native actor from the
locked parent and requires current account/security, client/MFA and group eligibility.
Canonical identity, provider provenance and completed LEGACY transfer remain pinned.

Ordinary access separately requires the gateway access deadline to be live. The
shared gateway metadata policy intentionally permits expired access for refresh;
that behavior must not be mistaken for ordinary access authority. Provider metadata
must also be live. Expired provider heads require the explicit native renewal flow;
this access use case neither performs login nor calls another use case.

Self identity decrypts only the exact observed provider generation. Capabilities
never decrypts a token or calls the provider. Each operation reserves one of V127's
seven ordinary gateway slots; renewal slot0 remains independent. Its deadline is
clipped to the original30-second budget, native admission, gateway access/hard bounds
and provider expiry. Saturation returns a bounded retry hint without hidden retries.

After prepare commits, identity uses the neutral provider `whoAmI(token, deadline)`
operation outside SQL. It never invokes company-shaped login or device logout. The
returned user/device must match the exact retained group device and must not be a
guest. Before and after I/O, including failed results, current admission, original
native version/security facts, provider generation and exact request token are
rechecked. A changed generation, offboarding, expired deadline or replaced request
withholds the response. A fresh request may acquire a later admitted native version.

Exact finally-release cannot delete a successor. Cancellation/interruption propagate;
an interrupted thread may leave only a finite lease for the existing worker cleanup.
No original company runtime slot is reopened and no retained key, identity or local
history is deleted.

## Validation and remaining work

Focused actual PostgreSQL tests cover identity, metadata-only capabilities, malformed
namespaces, expired access, minimum client build, concurrent saturation, retained
renewal capacity, replaced requests, native/provider generation changes, denial on
both provider outcomes, cancellation and native purge. An isolated pinned Synapse
case verifies the original group identity after restart without a company runtime.

Retained-device and HTTP integration checks additionally connect completed legacy
provenance, expired-provider native renewal, group bootstrap and identity/capability
protocol dispatch. Shared module DI requires actual API and full worker validation.
[Accepted evidence](../measurements/collaboration-space-gateway-access-2026-11-04.json)
records exact outcomes, source/report hashes, actual provider/HTTP cases and
reproduction commands.

Room preparation and [recipient query/claim](space-recipient-key-exchange.md) are now
implemented separately. [Encrypted to-device transport](space-to-device.md) now adds
confirmed recipient and sender verification. Room content/sync/push and Mobile
adoption remain required.
Keep original SDK namespaces, passphrases, owned history and uncertain sends. All54
communications acceptance groups remain open; no product rollout is implied here.
