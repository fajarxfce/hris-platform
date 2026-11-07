# Matrix gateway rejection diagnostics

The phone's native send queue reports HTTP403 without the HRIS-specific error code.
The HTTP response adapter now records a finite server-side rejection reason so a
subsequent attempt can distinguish recipient, room, company, client and assurance
failures. Security-filter denial has its own constant endpoint-unavailable category.

Only exact known static codes enter the log; every other code becomes `other`.
No request path, identity, token, failure parameters, message content or exception
is recorded. Non403 responses do not produce this diagnostic. Existing HTTP status,
Matrix error, HRIS error code, cache headers and permission checks remain unchanged.

Delivery/privacy tests and the actual API/security graph with a bounded fake Matrix
provider pass52 cases in82.676s, zero failures/errors/skips,8,244 unchanged inputs.
Format and architecture pass. The API bootJar contains the verified diagnostic class.
No SDK send replay, schema change or worker graph change was required.
[Evidence](../measurements/collaboration-gateway-rejection-diagnostics-2026-11-07.json).

The original phone pending report remains open until the actual rejection is
identified and corrected. Diagnostic delivery is recorded separately from source
validation; it does not claim successful message delivery.

API-only development delivery passed10.689s from061cac31. The exact verified image
retains the existing Java base, environment and schema116.1; worker, provider, storage
and database containers stayed unchanged. A root-owned local Docker copy required an
ownership correction during image verification; the same image was retained.
[Delivery evidence](../measurements/collaboration-gateway-diagnostics-release-2026-11-07.json).

The next diagnostic revision distinguishes `room_send`, `to_device` and `general`
using a typed route label. No URL or target identity enters the log. Existing52
delivery/API cases pass in81.557s with8,244 stable inputs. The server has observed
`collaboration_recipient_unavailable`; the exact operation/target remains unknown.
Read-only metadata finds one current device and one revoked-parent device for the
caller, while Bima retains one current device. This is a hypothesis about stale
recipient metadata, not proof of the actual failed request.
[Operation evidence](../measurements/collaboration-gateway-operation-diagnostics-2026-11-07.json).

The typed operation diagnostic is live from2e75fe1e. API-only activation passed
11.048s at01:20:40UTC; image3a2ea23b1dd3908419f4c548141dad0a799f5935585f2175d5163d449c0f2524
contains accepted JARb77fc179817e12166b7ecf2528dec23099e9ebd6747630cbf394bcc4f02c82c8.
Schema116.1, worker10485916 and all other services/configuration remain unchanged.
[Delivery](../measurements/collaboration-gateway-operation-release-2026-11-07.json).
