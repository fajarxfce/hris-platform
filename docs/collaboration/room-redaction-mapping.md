# Redacted encrypted event history

Matrix returns empty encrypted content after redaction. The gateway previously required
ciphertext and removed unsigned.redacted_because, causing context503 in the actual SDK6
experiment. This exact protocol fix was first validated in the Group worktree82f2c817 and
then with Mobile6334c0c4 in three native scenarios. Both Company and adopted Space windows
now succeed with independently verified original/edit hashes and no5xx. Original-only and
mixed process-loss recovery retain the original transactions and offline confirmations.

The mapper accepts empty encrypted content only with a matched redaction, checking original
room/participants, event/target IDs, time and legacy/modern target agreement. It preserves
the original author separately from the redactor. Matched redaction metadata is retained
without recursively copying unsigned data. Own transactions are preserved; peer transactions
remain hidden. Plaintext, contradictory redaction metadata and foreign ownership stay rejected.

The Company branch copies the accepted mapper and ten-case regression byte for byte.
Thirteen applicable existing Company history/sync/transport cases also pass; API and worker
boot JARs, formatting and architecture checks succeed in158.561s. Initial planning expected24
cases by copying Group counts; its extra fresh-HRISG-namespace transport case is not present
in this branch. The untouched Company test has two cases, making23 applicable cases. No test
was removed. Exact source and case-identity audits pass across8,293 fixed inputs.

No schema, DI or configuration changes are included. Relative to livee558a686 the only
application code change is this mapper; previously accepted recovery-verifier tooling is
also in Git. Activation is separate and must retain schema116.4/readinessON/groupOFF,
configuration and keys. The old API/worker images remain available for image-only rollback.

[Measurement](../measurements/collaboration-room-redaction-mapping-2026-11-10.json).

API and worker90163c32500ff2fa005013eaf83b026d4bdcf5ff are now active. Final activation
passes in25.060s, preserving schema116.4, all11 confirmed key publications, environment,
mounts, configured static addresses, published ports and other services. Localhost and
WireGuard health both pass. Worker readiness and readiness control remain enabled.

The first attempt automatically restored the old images after an overly strict check
rejected a changed dynamic bridge address. Docker had correctly updated its published-port
routing; no application failure was observed. The final attempt verifies the actual endpoints
and configured static addresses. A prior staging guard also assumed the old readiness-control
setting; its read-only preflight was corrected to preserve the current enabled setting.
Old images remain tagged for image-only rollback. No migrations or employee messages ran.
[Rollout measurement](../measurements/collaboration-room-redaction-rollout-2026-11-10.json).
