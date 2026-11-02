# Native recovery discovery and admission

`GET /api/v1/collaboration/native-devices?limit=8&afterCompanyId=<UUID>` discovers
retained device bindings for the authenticated account's current native parent.
Only native transport is accepted. The caller cannot choose an account or parent.
The page contains `accountId`, `nativeSessionId`, `devices` and nullable
`nextAfterCompanyId`. Each reference contains only `companyId`, `deviceId`,
`version`, `state` and `platform`. There are no company names, provider credentials,
callback keys or message contents. Responses use `Cache-Control: no-store`.

Limits are 1–32, default eight, with one lookahead. The seek is ordered by company
UUID; it describes a live page rather than a snapshot across multiple requests.
The current account and native parent are locked/revalidated, including credential
version, parent version, revocation and current access expiry. A 30-second budget,
clock rollback or interruption prevents publication. Invalid repository ownership,
ordering, duplicate, version, cursor or future-created metadata is rejected.

Account-owned device metadata already has the required forced-RLS scope in V104.
No new policy, migration or definer function is added. Discovery includes retained
PROVISIONING, ACTIVE and REVOKED references after company permission loss or MFA
expiry. The exact metadata handler uses `PendingMfaAllowed`, like authentication
self-service metadata, while domain checks still enforce the current account and
native parent. This cannot admit content, issue credentials or enroll a device.

`GET /api/v1/companies/{companyId}/collaboration/native-device/admission` separately
invokes `AcquireNativeCollaborationAdmission`. It requires current company/member,
account/native-parent, active bound device, applicable MFA and client policy under
their existing ordered guards. Its response contains company/account/device IDs,
device/native-session/credential versions and `admittedAt`/`expiresAt`, with a
maximum 60-second lifetime. It carries no token and has no pending-MFA exception.
The gateway independently authorizes every provider request. Clients must account
for request latency and their local clock instead of treating server timestamps
or discovered references as offline authorization.

[Evidence](../measurements/chat-native-recovery-directory-2026-11-02.json) retains
six new SQL scenarios, actual native HTTP routing and a separate MFA-enabled HTTP
case. The latter enrolls a fictional account through real endpoints and obtains a
native session, then proves expired MFA denies company admission while own metadata
remains discoverable. Initial fixtures inherited MFA-disabled configuration; the
strict fixture exposed and verified the metadata interceptor correction. API and
worker application contexts resolve the new shared DI binding.

Mobile must still implement finite background SDK ownership, persisted pagination
for collapsed push/reconnect/periodic recovery and per-company current admission.
The selected foreground workspace must not be changed to run background work.
Keep push delivery disabled until receive/decrypt/privacy and durable inline reply
are usable. These endpoint checks do not accept full chat notifications, live
Synapse/FCM delivery, load capacity, Apple or browser behavior.


## Native priority before the incoming sweep

The Android worker fixture now injects one strict metadata-only push hint for the
oldest of175 offline peer events. The actual application receiver persists its
encrypted queue and independent wake; after container reopen, fresh HRIS admission
and summary-by-room lead to native Matrix original acquisition. The point read must
retain the exact original/body. Company, room, Browse and Incoming positions stay
unchanged, the exact hint completes, and the global wake remains for the fair sweep.

[Evidence](../measurements/native-priority-2026-11-02.json) covers queued, frozen-draft
and cancelled-draft process-death variants. Each requires one priority turn (12
retained originals around its anchor), then11 incoming turns/reopens and176 originals;
foreground browsing independently takes11 pages/186 references. The gateway asserts
one room lookup and counters11 directory/12 admission/11 summary-page requests.
Ciphertext and exactly one worker send remain checked. No FCM or OS push receipt is
injected, and this proof does not run the full OS recovery coordinator. The separate
company WorkManager regression uses synthetic directory/company work. These scopes
must remain distinct from phone notification or communications feature acceptance.
