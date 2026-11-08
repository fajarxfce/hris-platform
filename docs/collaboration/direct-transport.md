# Native device and direct-room transport read

`GET /api/v1/companies/{companyId}/collaboration/direct-conversations/{conversationId}/transport`
returns the current native device and its published direct-room coordinates together.
It replaces the need for three separate metadata requests at one client admission point.
Existing device, conversation and room endpoints remain compatible.

The use case owns one company-scoped transaction. It takes the room guard before shared
policy, company, membership and account guards, then locks the current native parent.
Current chat permission, native session/version, client platform/build and effective
company policy are checked before acquisition and again before returning any result.
Late expiry overrides successful, missing and failed metadata reads; interruption
propagates. The endpoint performs no provider I/O, provisioning, writes or retries.

Only a direct participant can read this snapshot. Its active, unrevoked device must
belong to the same company, account, native session and client platform. Published room
ownership, job, provisioning version and configured Matrix server must match their
retained pointer; malformed or unpublished rooms are withheld. A peer's later departure
does not hide the caller's retained room metadata. Each actual Matrix operation retains
its own current gateway admission and recipient checks.

The bounded response contains company/account/native-parent IDs, device ID/version/
platform, conversation/participant IDs, room ID and provisioning version. It contains
no token, private key, label or message content. Responses use `Cache-Control: no-store`.
This snapshot is not a reusable sending credential and must not become a cached grant.
Clients still check their captured local scope after suspension and re-admit each
independent delivery phase.

[Validation](../measurements/collaboration-direct-transport-2026-11-08.json) passes 218
active cases with zero failures/errors: five PostgreSQL cases, four HTTP cases,69 OpenAPI
checks,18 delivery cases and122 worker cases. One unrelated existing opt-in elapsed
retention case is skipped. Source fingerprints remain unchanged through the gate,
both application dependency graphs run, and formatting/architecture/JAR checks pass.

The compatible API-only rollout of e8d1d24f is healthy and verified: exact JAR/image,
original runtime settings, other service identities and schema116.1 are preserved.
No migration ran. The rollback image and overlay are retained. Mobile adoption is
still under test, and no latency improvement is claimed. Mobile67 measured
2,663 ms before its encrypted request and197 ms to headers in one emulator sample.
Reducing metadata requests must be verified against the accepted combined release.

## Recipient readiness

The response now includes recipientReady, computed from the bounded existing
eligible-device projection using the same pure readiness policy as room sending.
The pair's account guards are acquired in stable ID order. Empty or unconfirmed
devices return false without hiding retained room metadata. Receiver short-token
expiry and offline status do not remove confirmed eligible keys; hard expiry,
revocation or loss of active chat access do. Failed or malformed projection reads
cannot claim readiness, and caller expiry still overrides late results.

Clients use false to retain blocked originals instead of scheduling transient
retries. Actual sending retains independent gateway admission. This additive fact
does not implement a readiness event feed or create receiver keys.

[Readiness validation](../measurements/collaboration-recipient-readiness-2026-11-08.json)
passes226 active cases (one existing opt-in retention skip), including receiver
publication/offline/revocation, late acquisition and interruption, HTTP/OpenAPI,
both application dependency graphs, boot JAR, format and architecture. All8,259
source inputs remained unchanged. Deployment and Mobile device acceptance are next.
