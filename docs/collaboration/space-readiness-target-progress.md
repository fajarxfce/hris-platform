# Acknowledging readiness under current target guards

V140 aligns queue acknowledgement with the receiving account's transaction. V139
allowed cursor advancement only in the publisher scope, while account/native/device
admission requires the target's RLS scope. TransactionRunner rejects nested owners.
A target's guarded validation and cursor acknowledgement must be able to roll back
together; changing SET LOCAL inside a datasource or dropping those guards before
acknowledgement would put the responsibility in the wrong place.

The successor migration allows an existing worker lease to advance either in its
publisher scope or in the account scope of the exact next cursor. Worker capability,
null HR-company scope,unexpired exact lease,immutable publication identity,strictly
forward cursor and attempt reset remain required. An unrelated account cannot advance
another target's cursor; ordinary API roles remain excluded by FORCE RLS even with
table grants. These are internal worker coordinates,not a public client capability.
The owning use case still proves the target came from its fenced projection and
revalidates the current native/device/provider identity around delivery.

Release remains publisher-only. A transport deferral exits the target transaction
before settling unchanged publication progress in a publisher transaction. Successful
or suppressed target advancement can instead commit with its final target checks.
Exact unregistered push-token cleanup can share that target transaction later.
No new fanout loop,provider call,credential refresh,key operation or DI binding is
introduced by this migration. V139's published bytes remain unchanged.

[Audited validation](../measurements/collaboration-space-readiness-target-progress-2026-11-09.json)
passes six actual repository tests in125.271 seconds,zero failures/errors/skips,
plus41 Python migration/recovery cases,on8,717 stable inputs. The guarded-recipient
regression first fails under V139,then proves failure/cancellation after the cursor
write roll back in the same target transaction. Unrelated actors,wrong target scope,
selected HR-company scope and ordinary API mutation are rejected. Target release is
still rejected; publisher release succeeds. A V139-to-V140 test preserves every
publication/key/revision/channel row and the active original lease before advancing
it under the receiving scope. Existing repository regressions also run on V140.

The Group recovery bound is140 with the same encrypted-envelope projection. Root
Platform remains116.4. [Pure/current target admission](space-readiness-admission.md) is now accepted separately.
Next implement bounded Matrix/PUSH fanout,then Mobile durable consumption/background ownership. Live
schema116.4,group routing OFF,phone87 and SDK6 are unchanged;all54 PRD groups stay open.
