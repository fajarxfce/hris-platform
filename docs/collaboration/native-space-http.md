# Native organization chat setup API

The following routes require a native bearer, current account/native session,
current chat eligibility in at least one active company, MFA when required, and
`X-HRIS-Client-Platform` / `X-HRIS-Client-Build`. Group chat remains disabled by
default through `HRIS_COLLABORATION_SPACE_ENABLED`; minimum Android/iOS builds use
the corresponding `HRIS_COLLABORATION_SPACE_MIN_*_BUILD` settings. This does not
enable a Mobile caller, group Matrix gateway or room exchange.

The root is `/api/v1/collaboration/spaces`. A selected HR company is unnecessary.

| Method | Suffix | Response |
| --- | --- | --- |
| GET | `/current` | Current space/account/native IDs, versions and short observation deadline |
| GET | `/{spaceId}/contacts` | Eligible account names and bounded public company labels |
| POST | `/{spaceId}/identity` | Canonical provider-user selection receipt |
| GET | `/{spaceId}/native-device` | Nullable current device and safe provisioning status |
| POST | `/{spaceId}/native-device` | Explicit SPACE or LEGACY registration receipt |
| POST | `/{spaceId}/native-device/retry` | Explicit ended-request retry receipt |
| POST | `/{spaceId}/native-device/legacy-runtime/freeze` | Exact-target original runtime freeze receipt |
| GET | `/{spaceId}/native-device/legacy-runtime/drain` | Actual bounded remaining original requests |

Mutations require `Idempotency-Key`. Persist the original operation UUID, account,
native parent, space, payload and observed versions. The public receipt contains
only `id` and `version`; replay returns that same receipt after current admission.
Selection and registration perform no provider I/O. Retry uses exact device and
provisioning revisions, preserves prior outcome and respects the existing cooldown.
Polling status neither retries work nor renews its original native proof.

Contact search is literal display-name text, at most 80 characters. Pages contain
1–50 accounts, exclude the caller, and use an account UUID cursor. Each contact has
at most eight public company labels plus an overflow flag. Login email and private
HR fields are excluded. Labels do not grant access to those companies or imply a
ready provider room. The client must handle a terminal cursor without another request.

Freeze accepts the exact target device, original observed device version and key
fingerprint. Drain uses those same coordinates. A completed transfer retains its
original freeze receipt and requires exact completion provenance. Zero server requests
is not proof of SDK shutdown. Follow [mobile handover](mobile-runtime-handover.md)
before calling freeze; it is irreversible for that original runtime.

[Evidence](../measurements/collaboration-space-native-http-2026-11-04.json): 137
domain/API/OpenAPI checks and the full 127-case worker suite (126 active, one existing
opt-in skip), including native transport, independent employee accounts, cross-company
directory visibility with denied HR access, revocation, exact receipts, safe status,
default-disabled deployment and generated contract validation.
