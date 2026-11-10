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

The stronger actual SDK6 experiment passes all three cases in 214.303s with Mobile6334c0c4.
Both original-history windows now succeed with independently checked original/edit hashes,
exact acquired anchors, and zero observed5xx. Missing-key, redaction identity and adoption
checks remain. Original-only and mixed Company/Space process-loss recovery preserve the
original transactions, confirm exactly two/three provider events with the recipient offline,
and read all confirmations after another offline restart. SDK/source/APK/case audits pass.

The event experiment records28 Matrix requests versus26 before the fix, adding two bounded
reads for edit heads absent from the presentation timeline. Native requests remain2; encrypted
sends4/sync3/optional secret-storage2 remain unchanged. This does not measure employee-phone
latency. The2,560MiB emulator is stopped. No live/phone change or Group schema rollout occurs.

[Protocol measurement](../measurements/collaboration-room-redaction-mapping-2026-11-10.json)
and [native measurement](../measurements/collaboration-history-anchor-native-2026-11-10.json).
