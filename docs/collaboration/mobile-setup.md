# Mobile native setup ownership

## Owned room timelines

Mobile [0ef8018c](https://github.com/fajarxfce/hris-mobile/commit/0ef8018c)
adds bounded room/timeline children under the protected SDK lease, including
partial acquisition, late cancellation, failed cleanup retention and parent drain
ordering. The native listener applies ordered deltas atomically while retaining
virtual positions and disposing every native callback value. Text, redaction,
unavailable keys and send status are copied into typed domain snapshots.

[Evidence](../measurements/collaboration-owned-timeline-2026-10-31.json) records 15
new common cases; final root/browser/Android/Wasm/lint passes in 245s with 2,160
desktop cases, 64 data browser cases and zero lint errors. Actual Android SDK case
`5aea81cc` passes in 34.340s (53s Gradle, 32 driver cases), checking both timeline
replies after process death, sibling survival and closed snapshots after parent
cleanup. All 15 Platform/120 Mobile source hashes are unchanged. One provider-busy
key upload returned 503 before successful completion; emulator is stopped.

Conversation command hosted control `39217be2`/`41248280288` also passes all jobs
and 26 steps: 143 desktop, 119 browser and 119 iOS simulator cases; Android/Wasm/
Apple compilation succeeds. All 26 command source hashes match the immutable
commit. [Retained command evidence](../measurements/collaboration-conversation-commands-2026-10-31.json)
records it; the actual HRIS run remained in progress at this recording.

**Next:** admitted common content ports and app entry, product history windows,
owned offline projections, bootstrap/cross-family recovery, logical reconciliation
and chat UI. The platform observer opens known rooms only and gates vectors above
512 entries. Apple native chat remains pending. No product acceptance, live/schema
change or phone installation is claimed; continue all P0–P8.

## Durable conversation commands

Mobile [39217be2](https://github.com/fajarxfce/hris-mobile/commit/39217be2f63116dfe2752672a68f25b9479c514b)
now persists per-peer/room control intent before submission, saves uncertainty
before HTTP and preserves operation/action through lost responses and ordinary
app upgrades. Exact-record removal requires an observed owned conversation or
provisioning version. Known server denials survive failed transport retries in
both setup and conversation replay. Preparation remains live-admitted native
control; offline message composition and UI are separate pending work.

[Evidence](https://github.com/fajarxfce/hris-mobile/blob/39217be2f63116dfe2752672a68f25b9479c514b/docs/measurements/collaboration-conversation-commands-2026-10-31.json)
records 24 new cases, final root/browser/assembly/Android/lint passing in 4m:
2,145 desktop, 70 domain browser and 49 data browser cases; zero lint errors.
Initial moved-ciphertext fixture reused a unique sort key and was corrected before
rechecking the cryptographic boundary. No device, live service or schema change.

CI `767ed980` lets running control/HRIS proofs complete while replacing superseded
pending work. Mobile `fbb2f3ae` provider run `41246770541` passes both modes/all
steps: classic 83.930s/native 92.938s, two browser cases each, cleanup complete;
all 14 Platform source hashes per mode match `e0dbb567`. The owned-sync measurement
retains those provider-only results separately from its actual HRIS lifetime proof.
Control `41246770522` and HRIS `41246770543` also pass every job and step: 119
desktop, 103 browser and 103 iOS simulator cases; Android/Wasm/Apple compilation
passes. The actual HRIS SDK case `001256c3` passes in 70.969s with cleanup complete;
94 Mobile and 15 Platform source hashes match their pinned commits. These results
are recorded in Mobile `a07c90e1`; Apple native chat runtime remains pending.
Starter hosted AppAuth verify `41245287269` passed all 15 verify steps at `9740cb5`
with matching source hashes; closure is committed at Starter `36116b2`.

**Next:** product room/timeline ownership and chat UI, owned offline projections,
bootstrap/cross-family recovery and logical-message reconciliation. Continue all
P0–P8; no product group is accepted.

## Owned synchronization and conversation control

Mobile [fbb2f3ae](https://github.com/fajarxfce/hris-mobile/commit/fbb2f3aef2b62fa221ae12d0c5f5b71d3ee1b22f)
owns classic sync through the protected SDK lease. Cancelled/late callers cannot
abandon the native producer, and cleanup terminates sync before database draining
and store unlock. Eight new common cases and actual Android process death pass;
[measurement](../measurements/collaboration-owned-sync-2026-10-31.json) binds 15
Platform/94 Mobile source hashes to proof APK `e5c07ded`. Actual case `54ab8f5a`
passes in 35.750s, one API/32 driver cases, cleanup complete. Root/Wasm/Android/
lint passes in 192s with 2,121 desktop and 45 data browser cases. One provider-busy
key-upload response is retained; the SDK exchange subsequently completes.

Mobile `50964794` also connects native participant, DM and room control adapters
and admitted reads, with bounded responses and strict scope/cursor validation.
Mutation ports preserve operation IDs and observed versions but still require
durable preparation/replay before UI use. Product room/timeline ownership, chat UI,
bootstrap recovery and offline logical-message reconciliation remain next. No
feature group, live rollout or physical phone action is claimed.

## Application runtime checkpoint

Mobile [9f83dcdc](https://github.com/fajarxfce/hris-mobile/commit/9f83dcdc2f42e69441569f050c5428746ef38f8a)
now installs collaboration DI and an Android Application lifetime owner. Admitted
bootstrap installation retires only the observed encrypted command after protected
SDK installation; restoration reopens existing facts without re-enrollment. Late
account/company/permission changes close acquired resources. One application
observer closes obsolete owners and owns deadline timers; startup does not create
SDK clients or issue credentials. Other hosts report unavailable native support.

[Application evidence](https://github.com/fajarxfce/hris-mobile/blob/9f83dcdc2f42e69441569f050c5428746ef38f8a/docs/measurements/collaboration-application-runtime-2026-10-31.json)
records final root/Wasm/Android/lint passing in 70s: 2,093 desktop cases and zero
lint errors; domain browser has 51 passing cases. The manifest AppAuth dependency
fix and lint guard are independently verified and pushed to Starter `9740cb5`
(331 desktop cases, dev assembly, zero lint errors). Hosted checks remain separate
from these local results. No device or live service changes.

Corrected provider protocol run `41242901470` at Mobile `3cedb0b1` passed both
classic/native jobs and all steps against Platform `e0dbb567`: 77.293s/75.327s,
two browser cases each, cleanup true, all 14 Platform source hashes verified.
Mobile retains this in its offline-signal measurement at `9f83dcdc`. The earlier
non-atomic IPC failure remains recorded, not overwritten by the correction.

**Next:** native conversation/participant/room adapters and chat UI, SDK sync/room
children under the same shutdown owner, bootstrap uncertainty/build recovery and
offline logical-message reconciliation. This is not product chat acceptance; all
P0–P8 work continues. The previous checkpoint below is historical.

## Original setup-command checkpoint

Mobile [fb1b1c13](https://github.com/fajarxfce/hris-mobile/commit/fb1b1c13ef2ddce96d8fb3ab341a108d905d32f6)
implements the native setup HTTP adapters and durable registration, provisioning
retry and gateway-bootstrap commands. The app captures current HRIS company/native
family admission, saves immutable input before transport and retains uncertain
attempts. Retries preserve the operation and versions; bootstrap also pins its
client build. Current admission is reacquired after pending storage and HTTP.

Separate AES-GCM envelopes bind requests/outcomes to audience, account, company,
native family, operation, purpose and revision. The active local session is checked
transactionally without becoming a permanent ciphertext owner. Missing keys,
ordinary deadline expiry and account changes preserve stored evidence. Gateway
credentials remain encrypted until SDK installation can be confirmed. Only an
observed confirmed registration or new provisioning-job receipt can retire through
the setup reconciliation use case.

[Mobile evidence](https://github.com/fajarxfce/hris-mobile/blob/fb1b1c13ef2ddce96d8fb3ab341a108d905d32f6/docs/measurements/collaboration-setup-commands-2026-10-31.json)
retains 39 source hashes and final 62s root/assembly/browser checks: 2,040 desktop
cases, collaboration domain 23/data 15, browser domain 23/data 7; no failures or
skips. Its preceding CI exposed an obsolete SDK setup default and then missing
Wasm workspace lock entries. Both are corrected; inspect the new exact hosted
control run. Apple runtime acceptance remains pending.

The module is not yet installed into app navigation/DI. Next is the protected SDK
store/session owner and actual message queue restart, followed by bootstrap recovery
after a lost replay/build change, chat UI and all P0–P8. The
[SDK lifecycle findings](https://github.com/fajarxfce/hris-mobile/blob/fb1b1c13ef2ddce96d8fb3ab341a108d905d32f6/docs/collaboration-sdk-lifecycle.md)
cover the required exclusive store lock, one restore per client, queue pause before
restore/listener attachment, and void token-persistence callback. No reset/logout,
message durability, product-feature or capacity acceptance is implied. No Platform
production code, schema, live service, physical phone or Starter changed here.
