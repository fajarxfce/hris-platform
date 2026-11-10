# Redacted encrypted events through the gateway

A valid redacted Matrix encrypted event has empty content. The old mapper required an
algorithm and ciphertext for every encrypted event and removed unsigned.redacted_because.
The actual SDK6 experiment reproduced context503 with collaboration_provider_invalid_response.

The mapper now accepts empty encrypted content only with a matched redaction event. It
validates the redaction type, event and target IDs, timestamp, original room and captured
participants. Legacy and modern target fields may coexist only when they agree. The
original sender/event identity stays separate from the redaction actor. Ciphertext with
redaction metadata, absent/contradictory metadata and foreign ownership remain rejected.

Matched redacted_because is retained without recursively forwarding nested unsigned
metadata. The original sender keeps its own transaction ID independently; peer transaction
IDs stay hidden. Standalone redactions preserve their own transaction under the same rule.
The existing Company and Space history/sync consumers share this protocol mapping; current
caller admission and state-event checks remain in place.

Ten new protocol cases and32 existing Company/Space history/sync cases pass in137.027s,
with all previous case identities preserved. The original nine-case regression failed five
cases before the fix. The first repair passed41 cases; review then added the own-redaction
transaction case. Format, architecture and server test-source compilation pass. The final
source manifest contains8,740 stable inputs.

The native fixture now requires both original-history windows to succeed with correct
original/edit hashes and zero observed5xx. That stronger experiment awaits the Mobile
edit-anchor fix and a newly compiled proof APK; this increment does not claim its execution.
No schema, dependency injection, live deployment or employee-phone change is included.

[Measurement](../measurements/collaboration-room-redaction-mapping-2026-11-10.json).
