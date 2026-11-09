# Current company fixes with organization chat

Accepted source reconciliation of group bf6e176a and company/root4d4df31f, whose
common ancestor is8bad394e. This is a source merge, not a deployment. Live schema116.4
and runtime remain unchanged; group V117–138 stays disabled.

Both native runtime-transfer exclusion and the company's accepted default-key read
are retained in the company gateway. DI supplies both repositories. Worker composition
retains separate group-push, FCM-readiness and Matrix-readiness jobs, each with its own
existing enablement conditions, bounded executors and shutdown ownership.

The group branch had extracted a shared provider sync reader before the company
readiness-control change. The merged reader accepts explicit optional company framing;
only the company repository supplies it. The existing bounded, exact-own-user/control
projection is retained. An adopted group runtime must still reject a company control,
even when its physical Matrix user/device is unchanged. Original encrypted to-device
frames, group/original room framing and cursors keep their existing behavior.

The merged default-key HTTP endpoint initially routed only company tokens. The fixed
group operation uses the existing group admission/request/credential lifetime around
one provider read. It checks the exact own Matrix user, revalidates both provider
outcomes and closes its request on cancellation. Missing metadata is404, an existing
empty object is200, and a present key identifier is preserved. It writes no key,
backup, provider account or SDK state. Controller token dispatch and DI are explicit.

The recovery verifier's explicit whitelist did not include reviewed116.2–4. One new
regression failed before correction;27 migration/recovery Python cases then pass.
Those migrations add raw readiness queue/control/revision metadata without changing
an encrypted envelope. Unknown versions remain rejected; numeric migration ordering
places116.1–4 before117. This does not establish live archive/restore acceptance.

## Accepted validation

Eight textual conflicts are resolved. Pinned ktfmt0.59 formats65 changed Kotlin inputs
in3.651s with256MiB heap; three files changed. New source tests include group isolation of
company readiness, three actual PostgreSQL/native default-key admission cases and
one actual HTTP dispatch case. The focused and full runs cover these cases. Validated:
format/architecture, company/group sync projections, current native gateway/room
behavior, migrations/RLS, API context and the FULL worker context suite because shared
DI changed. Preserve every first failure and source manifest; no live source replacement.

Private evidence and scripts are in `.work/space-root-reconciliation-2026-11-09`.
Current follow-up work and Mobile proof are in [agent handoff](../agent-handoff.md).

First focused49700 passed207 domain/data cases but failed API test compilation:
Root renamed the private directMetadata fixture to accountData, while five Group-only
callers survived textual merge under the old name. The correction uses the merged
fixture with its unchanged m.direct default; no production behavior or assertion is
weakened. Focused67950 additionally selects the existing group metadata HTTP case.
Pinned formatter accepts this correction unchanged. First failure is retained.

The original Platform branch independently ports the reviewed schema allowlist in
691f3fc5, with40 passing Python cases and its own116.4 maximum. Group retains138;
these are distinct schema bounds. Neither change performs a live restore.

Second focused67950 ran two HTTP cases and failed the new default-key assertion:
the groupNative fixture freezes time, while the real request lease starts at the
later PostgreSQL clock_timestamp. The operation correctly returned request_lost.
Only this real-provider-operation fixture now follows system time; request lifetime
checks and expected503 provider-unavailable remain unchanged. Corrected focused24065
passes both HTTP cases in75.409s. Full33511 passes5,607.828s on8,686 unchanged inputs:
1,444 fresh cases, zero failures/errors and one existing elapsed-retention opt-in skip.
Database138, domain189, data762, delivery25, API180 and complete worker150 are verified
from archived fresh XML. Both readiness worker contexts and all new regressions ran.
Format, architecture, image pins, migration and recovery verifiers pass. Earlier207
focused cases are separate proof, not recounted.

[Accepted measurement](../measurements/collaboration-root-source-reconciliation-2026-11-09.json)
retains source/report hashes and both first failures. This accepts the source merge;
no live runtime, migration, group routing or Mobile/phone activation is implied.
