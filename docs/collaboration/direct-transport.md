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

No migration is required. At this checkpoint the endpoint is not deployed, Mobile
adoption is still under test, and no latency improvement is claimed. Mobile67 measured
2,663 ms before its encrypted request and197 ms to headers in one emulator sample.
Reducing metadata requests must be verified against the accepted combined release.
