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
