# Company client policy

Client policy controls module availability, minimum client builds, and scheduled maintenance for one company. It does not grant permissions, change employment scope, or attest a device. The server validates authentication, resource access, commands, and payloads independently.

## Client contract

`GET /api/v1/companies/{companyId}/client-policy` is available to a current company member, including during maintenance or when their build is obsolete. It returns:

- `schemaVersion: 1` and the active nullable `version`.
- `enabledModules`, `minimumBuilds: {android, ios, web}`, and optional `maintenance: {startsAt, endsAt}`.
- `maintenanceActive`, `serverTime`, and `validUntil` in UTC.

The default enables all supported modules, with minimum builds of zero and no maintenance. Missing configuration has `version: null`. The response excludes the editor, audit reason, and future revision contents. Responses use `Cache-Control: no-store`; protected client state owns any local copy.

Send paired headers on company business requests:

```http
X-HRIS-Client-Platform: ANDROID
X-HRIS-Client-Build: 42
```

Native bearer sessions use `ANDROID` or `IOS`; browser sessions use `WEB`. Builds are monotonic integers from 0 through 999,999,999. The headers can be omitted while the applicable minimum is zero. A native session without platform metadata must identify its build once either mobile minimum is nonzero. Existing clients retain access under the default policy. Self-reported platform/build metadata is an availability mechanism, not a security identity.

`validUntil` is at most 60 seconds after `serverTime`, shortened by a future activation or maintenance boundary. Cache policy under backend/account/company, refresh before flushing an offline queue, and revalidate on foreground/reconnect. An offline device cannot discover an administrative change. The server evaluates current policy for every new company business request.

## Failure handling

The dashboard sends `WEB` and its compiled build number. Set `HRIS_DASHBOARD_BUILD` when running its development server or build; the default is `1`. A new distributed build should use a higher number before raising the corresponding company minimum. The frontend translates availability codes without automatically replaying failed commands. A policy management screen remains part of the dashboard workflow work.

The dashboard accepts finite retry metadata up to the seven-day maintenance limit. An invalid optional retry hint is ignored without discarding the stable failure code or its safe localization parameters.

Translate stable codes and unlocalized parameters in the client:

| HTTP | Code | Parameters | Client action |
| --- | --- | --- | --- |
| 403 | `client_version_required` | None | Send the paired platform/build headers. |
| 403 | `client_update_required` | `platform`, `minimumBuild` | Pause the company's queue and direct the user to update. |
| 403 | `company_module_disabled` | `module` | Refresh policy and pause that module's commands. |
| 503 | `company_maintenance` | `endsAt`, `retryAfterSeconds` | Respect `Retry-After` and revalidate when the window ends. |
| 400 | `invalid_request` | None | Correct missing, duplicate, or malformed client headers. |
| 422 | `invalid_client_version` | None | Correct a platform incompatible with the session transport. |

These failures do not consume a business operation key. Retain the original queued payload, observed version, and operation key while resolving availability. An already committed command still requires current admission and authorization to read its receipt. An enabled module does not restore a revoked permission. Clear caches for access revocation according to the [mobile contract](mobile-api.md); a flag change alone is not account revocation.

Sync checks the effective authorized collection selection, including the established default when `collections` is omitted. A request containing a disabled capability fails instead of silently changing its cursor projection. Selecting only enabled collections is allowed; changing the selection requires a new bootstrap under the [sync contract](synchronization.md). Re-enabling a module preserves existing valid cursors, subject to ordinary expiry, retention, and access changes.

## Administration

These routes require current `settings.manage`; writes additionally require recent authentication, or recent MFA when configured:

- `GET /api/v1/companies/{companyId}/settings/client-policy` returns `{latest, effective}`. `latest` is the nullable configured head; `effective` has the public client-policy response shape evaluated at the same guarded read.
- `GET /api/v1/companies/{companyId}/settings/client-policy/revisions/{version}` returns an immutable revision.
- `PUT /api/v1/companies/{companyId}/settings/client-policy` requires `Idempotency-Key` and a complete replacement policy:

```json
{
  "expectedVersion": null,
  "activateAt": null,
  "disabledModules": [],
  "minimumBuilds": { "android": 42, "ios": 18, "web": 0 },
  "maintenance": null,
  "reason": "Require the supported mobile builds"
}
```

Null activation means immediate activation after acquiring command guards. An explicit UTC activation must still be in the future when a new command is accepted and be within 365 days. Receipt replay remains valid after that date. Revisions use microsecond precision and optimistic versions. At most 10,000 immutable revisions are retained per company.

The settings snapshot holds the shared policy guard through the latest-revision and effective-policy queries, including the next activation boundary. A concurrent write cannot mix a new effective policy with an old configured head. Access and applicable MFA assurance are rechecked after policy, company, membership, and account guards. A future latest revision can coexist with an older effective version, or with the default policy before the first activation. Clients should keep these concepts separate and use `effective.serverTime`/`validUntil` when displaying or refreshing the snapshot.

The effective configuration is the highest revision whose activation time has arrived. To cancel or replace a previously scheduled revision, save a higher immediate revision with the desired current values. The old scheduled revision remains in history and cannot later supersede that newer revision. A maintenance interval includes its start, excludes its end, and lasts at most seven days; expired windows have no effect.

Supported keys are `PEOPLE`, `WORKFORCE` (including overtime), `LEAVE`, `EXPENSES`, `PAYROLL`, `DOCUMENTS`, `COMMUNICATIONS`, and `REPORTING`. Shared approval administration remains available under module flags but follows maintenance/build admission. Business decisions remain gated at their owning feature endpoint. Identity, company administration, policy recovery, and job inspection/cancellation remain available for authorized recovery.

Admission precedes the business handler. Already admitted requests and durably accepted jobs may complete; configuration changes do not interrupt their transactions. Workers retain their execution access and lease policies. The feature-to-policy mapping belongs to the application composition; datasources perform persistence.

Global group reports have no company path parameter. Their use case checks the same client policy for every selected company under ordered policy/access guards. A typed HTTP argument supplies the authenticated transport and parsed build headers; client query values cannot replace this context. A blocked company rejects the whole report and adds its ID to safe error parameters. See [reporting](reporting.md).

Writes acquire the policy guard before company, membership, and account guards, then recheck access and recent authentication before replay or mutation. Head, revision, receipt, audit, and event commit atomically. Effective reads retain the shared policy guard through the current-revision and next-activation queries. The server needs no policy polling loop or in-memory scheduler.
