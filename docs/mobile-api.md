# Mobile API contract

The versioned API serves web and native clients. Native authentication, idempotent commands, optimistic versions, bounded pagination, resumable documents, and structured problems are implemented. [Incremental synchronization](synchronization.md) is implemented for owned expense claims, leave requests/balances, overtime requests, finalized payslips, and payroll payment progress. Send an explicit `collections` selection supported by the mobile version and persist it with its cursor; server expansion then does not introduce an unrecognized collection automatically. Other list endpoints do not implicitly provide a consistent snapshot or change cursor.

## Localizable errors

JSON failures use `application/problem+json` (RFC 9457). Controllers and authentication, CSRF, request-size, and admission filters expose the same fields:

```json
{
  "type": "urn:hris:problem:invalid_attendance_range",
  "title": "Unprocessable Content",
  "status": 422,
  "code": "invalid_attendance_range",
  "fields": {},
  "parameters": { "maximumDays": "31" },
  "correlationId": "a3979ee4-1cb0-4c58-a003-12a5e3c66c74"
}
```

Translate `code` in the application. For example, `invalid_attendance_range` can map to “Select up to {maximumDays} days.” or “Pilih maksimal {maximumDays} hari.” `fields` maps request field paths to validation codes, such as `timezone: invalid_timezone`; these are translated in the same way. Keep `title` and `status` for HTTP semantics, not product copy. Unknown codes need a generic local message and the correlation ID for support.

`parameters` contains explicit, safe values for interpolation. Numeric values are unlocalized strings, including decimal money; dates and timestamps retain their API formats. The client formats these values for its locale. Do not put employee names, submitted values, credentials, or technical exception messages in error parameters. `Accept-Language` does not change codes or parameters. Existing code meanings are retained; new codes and optional properties are additive within `/api/v1`.

The response `X-Request-ID` matches `correlationId`. Clients may supply a UUID in that header. JSON problems are not cacheable. Where admission control knows a delay, `retryAfterSeconds` matches `Retry-After`; its absence does not authorize immediate or unlimited retries. Responses interrupted after binary streaming starts cannot turn into a JSON problem: discard an incomplete download and resume using its authorized Range/ETag contract.

## Offline command queue

Store an outbox entry before sending a mutation. Keep the backend origin, account, company, resource ID, command route, original payload, observed version, and UUID `Idempotency-Key` together. Where the API accepts a resource UUID, generate it once on the client. Local optimistic state and server-confirmed state remain distinct.

1. Send an entry only under its original account/company. Serialize dependent commands for a resource; independently retry unrelated resources with bounded concurrency.
2. Keep the same key and identical payload when a response is lost or a retry is eligible. Server receipts return the original committed outcome, even if later edits have advanced the resource. Business writes, receipt, audit, and outbox commit atomically.
3. A 409 `operation_payload_mismatch` means the key was reused for a different command. It is a client queue error, not a retry signal.
4. A 409 `stale_version` requires fetching current authorized state and resolving the conflict. A revised intent gets a new operation key. Never silently overwrite the version and resubmit.
5. Validation and permanent authorization failures stop the entry. Temporary network/408/429/503 failures use a finite attempt budget, exponential backoff with jitter, and `Retry-After` where present. App shutdown, logout, company switching, or cancellation stops active work; reopening can resume persisted pending entries.

Not every conflict is a version conflict. Domain codes describe decisions such as a closed work period, legal hold, duplicate evidence, or unavailable approval. Client policy decides whether the entry needs correction, renewed authentication, a user decision, or explicit recovery. The API does not silently retry a business command.

Offline attendance remains evidence awaiting verification. Preserve the original `capturedAt`, device ID, event ID, and offline flag; the server records receipt time independently. Do not present an offline punch as accepted payroll time before supervisor verification.

## Authentication and cache scope

Use bearer sessions for native requests and keep refresh credentials in platform secure storage. Access tokens are short lived; refresh tokens rotate. Use one refresh operation at a time per session, persist the response atomically, and reuse its operation ID if that refresh response is lost. Competing independent refresh commands may trigger reuse protection. A revoked session requires authentication; repeated refresh loops must stop. MFA and recent-authentication failures require their specific authentication flow.

Namespace local data by backend origin, account, company, and client schema. Encrypt sensitive local data according to the device policy. Cancel obsolete requests before switching partitions, and reject late results whose account/company/session generation no longer matches. A 403 `company_access_denied` requires removing that company's cached data and stopping its outbox. Other denied resources must be removed when their access is lost. Logout clears credentials and account caches; never replay an old account's commands after another login.

Revalidate membership and permissions on reconnect and foreground entry. Offline clients cannot learn that access was revoked while disconnected; define a finite offline access period for sensitive content. Server authorization is rechecked independently of what the device has cached.

## Transport conventions

Treat endpoint cursors and tokens as opaque. Follow bounded pages rather than requesting an entire organization in one response. Store decimal strings without converting them through binary floating point. Use server UTC timestamps for synchronization, explicit IANA zones for schedules, and local dates for work-day policy. A device clock is evidence, not an authority for conflict ordering.

Resumable uploads use bounded chunks and stable operation keys. Finishing bytes is separate from successful validation. Authorized downloads support Range/ETag; do not cache storage URLs or assume a previously authorized document remains readable after access changes.
