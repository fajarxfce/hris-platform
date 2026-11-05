# Agent continuation — HRIS workspace

## Release 38 chat hub delivered; metadata delta implementation next — 5 November 2026

Mobile source34e68b128bd9fa80ccc817d8e008caf850920d96 and delivery docs37c3f05a are
committed/pushed. Non-debuggable ARM64 devRelease38/1.0.0 installed in place on the
phone at17:18UTC,60.322s including device SHA verification. APK88,403,668 bytes,
SHA256cbb6c3d1f6fcffde562a02584212354f561e6b1947cd48766c51a52e8581524e.
No phone UI operated or data cleared. [Evidence](https://github.com/fajarxfce/hris-mobile/blob/37c3f05a/docs/measurements/android-release-phone-2026-11-05-build38.json).
Do not reinstall; future installs require announcement and explicit install-only.

Chat groups Conversations/Contacts in one bottom destination. Existing directory
rows still lack account-linked chat actions. Names-only local emissions no longer
clear same-owner previews; duplicate emissions/identical metadata cause no detail
restart or encrypted rewrite. Empty Next no longer leaves latent navigation. Actual
HTTP metadata acquisition still uses UUID pages, NOT a delta feed; Matrix messages
already synchronize incrementally. This transport gap remains current priority.

Native Rani proof98.877s passes section/Newchat return, both draft previews, history,
offline reopening and cold offline process restart. HTTP probe confirms unavailable
server; Contacts also retained. Four captures reviewed. Rani SAMPLE-001 alone gained
people.directory.read via normal admin API, then Change company/reselect refreshed
workspace permissions. Existing collaboration.chat retained; temporary operator
session revoked. No identities, keys or messages reset; other memberships unchanged.
Local provision.py chat-only assertion is now stale for Rani; do not rerun blindly.

Retained Bima passes4history checks109.672s with8rows and no Older/decryption/history
failure. Owned receiver5562/PID2232286/startTicks157580400 runs38; verify current
start record. Rani stopped38; Nadia/Dimas stopped37. Preserve userdata AND sidecars/
keys, and never repeat the already accepted Forward publication.

Mobile full2 passed1,045fresh/399.451s with4,103 unchanged inputs; clean38build/lint
528.929s/zero lint. All local wrappers drained. MobileApple42069899173 exact34e68b12
passed simulator tests17:12UTC; full app still building17:20. Do not duplicate dispatch;
full reports/app/association audit pending. Starter batchApple42061944252 accepted
118cases/31suites with exact port; docs f99650d pushed. No Starter hub policy port.

Live backend11a98115/schema116 unchanged; chat pushOFF. New metadata migration must
precede the unshipped groupV117–137 range. Flyway dotted116.1 is a possible insertion
without rewriting either existing history; tool/generate_jooq.py currently parses
integer versions and must be fixed/tested before using it. No new migration exists
or is deployed yet. [Metadata contract constraints](collaboration/direct-summaries.md#incremental-metadata-follow-up).

Protected Mobile editing stasha34a7b (five unvalidated policy/decoder files) and
originalbfdef2ce remain. Immutable original send evidence,15-minute edit window,
account/device/admission isolation remain required. Continue metadata deltas,
account-linked contacts, edit/delete/groups/calls/meet; all54 PRD groups remain open.
Push every commit; user corrections do not cancel broader long-run work.

## All four fictional receivers ready; forwarding resumes — 5 November 2026

Phone release36 is installed from Mobilea26a1248 (delivery docsa5bc4190),
SHA256`6a3729ab16af62090b08cc1e210f7643344a3e22ad65fc9bd2baf8c06a7313c6`.
Sample1 publishes its first key without a provider room/message and survives cold
restart; retained Bima passes all four history/restart checks with eight rows and
no Older/decryption/history error. Samples3/4 now complete normal product login,
registration/bootstrap and Open chat on the same release36. Read-only DB confirms
all four fictional accounts each have one ACTIVE device and one confirmed key
publication. No message was sent to force setup. This is recipient setup evidence,
not a new message-exchange test between every pair.

Sample1/3/4 images are cleanly stopped. Bima runs on owned emulator5562/PID958042
(verify the current start record), release36; retain each userdata.img AND sidecars,
private SDK keys and native parents. Do not reset or copy stores between accounts.
Apple CI42036083914 passed simulator tests and is building the app for sourcea26a;
full job acceptance is still pending. Backend11a98115/schema116 unchanged.

Next forwarding work: Mobile restored ONLY the ten core/database batch files from
protected stashbfdef2ce into its worktree after exact baseline comparison. Forward
metadata stays stashed, and the stash itself is retained. Earlier batch118 focused
passed before stashing; add disk close/reopen plus session-replacement coverage,
then validate this current raw API and the actual Forward consumer. Do not claim
new source tests or copy HRIS metadata to Starter. Generic Starter port follows
Mobile validation. Continue edit/delete/groups/calls/meet; all54 PRD groups open.
Push each commit; announce EACH phone install and use --install-only.

## Device sync implemented; release36 native acceptance next — 5 November 2026

Mobile [a26a1248](https://github.com/fajarxfce/hris-mobile/commit/a26a124837637831655d19cc9dc89c1929e65e02)
is pushed. Enrolled devices now synchronize without room or outgoing-message
activity. Device sync and incoming acquisition share the first bounded phase;
existing company/admission budgets, SDK ownership and recipient publication guards
remain. First sync is not a key-publication acknowledgment.
[Contract and validation](https://github.com/fajarxfce/hris-mobile/blob/a26a1248/docs/collaboration-device-sync.md).
Full :check/Android/browser/proof pass521.721s,3,664 fresh cases,zero failures/errors/
skips,4,077 unchanged inputs. Earlier worker fixture expectation corrected before
that complete pass. Clean ARM64 devRelease36 build/lint is running; no release36
native or phone acceptance yet. Apple-only CI42036083914 runs this source;
42026950708 passed157a/release35.

User phone remains validated35 (older-history reopening fixed), installed in place
without UI driving. Bima is now cleanly stopped; retain userdata.img and sidecars,
SDK keys and messages. Sample1 retained release34/Home runs on owned emulator5562,
PID680459 (verify current owner record), one ACTIVE device/zero confirmed key
publications and no provider-room request/binding. Candidate36 must publish keys
without a first message/room, then retain the same device/keys across cold restart.
Mobile local release36 scripts enforce the owned image and artifact checks.
Samples3/4 Firebase/HRIS links exist; product device activation still pending.

Backend11a98115/schema116 unchanged; group V117–137 remain unshipped. Do not weaken
recipient guards, send artificial user messages or reset/clone SDK stores. After
native acceptance, activate remaining fictional receivers, announce EACH phone
install and use --install-only. Continue forwarding stashbfdef2ce (batch118 focused
passed), Starter generic-storage port, edit/delete/groups/calls/meet. All54 groups
remain open. Push every commit and preserve these continuation notes.

## Release35 history fix delivered; initial recipient activation next — 5 November 2026

Mobile source157a257d568c10f32e7159722d8fdd7f579b09e4 is installed in place as
ARM64 devRelease35 on10.77.77.3:5555. Announced install-only; signer/app ID/first
install time preserved; device SHA256 matches c227ba97083c3bccff4a42f113eb8ce8517411b3d97f2779ba646f266f575175.
Install32.829s. No phone UI driving. [Delivery and actual proof](https://github.com/fajarxfce/hris-mobile/blob/94dcdfb3/docs/measurements/android-release-phone-2026-11-05-build35.json).

Older messages no longer returns after reopening an exhausted room. Retained room
metadata is not counted as pending messages; actual unresolved messages remain
available. Source gate1,064 fresh pass506.364s/4,071 unchanged inputs; focused6.
Build/lint596.23s/zero lint. Exact APK on retained Bima account passes in-place
update, two room recreations and cold process restart108.748s: eight messages stay
visible with no older control or decryption/history error. Redacted image reviewed.
Bima emulator5562/PID429598 remains running; preserve userdata/sidecars/native parent.

User confirmed Bima send after receiver activation. Sample1 normal product setup
now has an ACTIVE device but no published key: an empty direct has no provider-room
request/binding. Incoming recovery skips NOT_REQUESTED rooms, and sync starts only
on timeline entry; investigate device-level initial sync before the first room.
Do not weaken recipient guard or send a message merely to force setup. Sample1
persistent data retained, emulator stopped; samples3/4 Firebase/HRIS links ready,
SDK activation pending. Backend11a98115/schema116 unchanged. Apple42026950708 runs
on157a; previous42017858911 passed745e. Protected Mobile forward stashbfdef2ce remains
incomplete (core batch focused118 passed). Continue activation, forwarding, groups,
calls and meetings; all54 PRD groups open. Group V117–137 remain unshipped; generic
batch storage still needs Starter port after validation. Push every commit.

## History fix pushed; first-room activation follow-up — 5 November 2026

Mobile157a257d568c10f32e7159722d8fdd7f579b09e4 fixes retained older-history end
restoration. Full :check/Android/browser/proof pass506.364s/1,064 fresh/4,071
unchanged source inputs; focused6 pass. Actual release34 receiver reproduces the
reported reopen bug. Clean signed release35 build/lint pass596.23s/zero lint;
APK SHA256 c227ba97083c3bccff4a42f113eb8ce8517411b3d97f2779ba646f266f575175.
It is installed IN THE OWNED BIMA EMULATOR ONLY for current-source reopen/cold
process proof. Phone remains34. Finish actual proof, exact cold-start smoke,
announce EACH phone install, install-only. Apple-only CI42026950708 is running on
157a; prior42017858911 passed745e. Live11a98115/schema116 unchanged.

Sample1 completed Firebase/HRIS login, registration, bootstrap and Open chat via
the normal product. Read-only DB shows one ACTIVE device, zero key publications,
one canonical direct but zero provider-room requests/bindings. Closing/reopening
the empty room did not publish keys. No message was sent to force activation.
This exposes a first-room activation follow-up: setup persists a recovery wake,
but a room without pending messages is not provisioned. Trace background runtime
activation/key upload before claiming all demo recipients are ready. Keep the
existing recipient guard and all original SDK identities. [Evidence](measurements/chat-demo-readiness-2026-11-05.json).

Sample1 emulator stopped cleanly; its userdata and sidecars are retained alongside
Bima's. Samples3/4 have Firebase/HRIS links and prepared separate persistent data,
but no product devices. Bima now runs emulator5562/PID429598 with its retained
identity. Stop/start ownership is in each private .work/demo-chat-device directory.
After release35 delivery, address initial key publication, then resume protected
forwarding stash bfdef2ce and the full communications plan. All54 groups open.

## Bima send accepted; older-history regression active — 5 November 2026

At12:02:36 the user confirmed previously pending Bima messages were sent. The
owned Bima receiver shows eight message rows, each with content/time, and zero
exact decryption placeholders. Message bodies are not logged or retained in this
evidence. [Updated readiness evidence](measurements/chat-demo-readiness-2026-11-05.json).
The recipient guard remains unchanged. Allfour fictional samples now have Firebase/
HRIS identity links; only Bima has an active SDK device and confirmed publication.
Other three still need normal product device setup. Do not clear Bima userdata or
sidecars, revoke its native parent, or treat this persistent receiver as disposable.

User priority: Older messages reappears after closing/reopening an exhausted room.
Mobile regression reproduces it with retained room-state references (two failures
in six focused cases); the pager fix is being tested. Preserve those references
without presenting them as messages; scan only owned local metadata, retain actual
unresolved messages, handle concurrent traversal changes/cancellation. Phone34 and
live11a/schema116 unchanged. Forwarding stash bfdef2ce remains intact.

Mobile Apple-only CI42017858911 on745e5a02 completed successfully: iOS simulator
tests and simulator app build pass. This is source745e acceptance, not the new
history candidate. Continue Mobile gate, validated release35, notify EACH install,
then resume the communications plan. All54 groups open; push every commit.

## Actual Bima rejection traced; receiver activated — 5 November 2026

Release34 user still reported pending old/new messages. Passive45s captured four
PUT Send403 with exact `collaboration_recipient_unavailable`; DB confirms allfour
fictional SAMPLE accounts had zero chat devices/publications. Directory enrollment
onNov3 explicitly did not provision recipient keys. The native queue recovery fix
now permits attempts; this second, demonstrated recipient guard is not weakened.
[Evidence](measurements/chat-demo-readiness-2026-11-05.json).

Bima SAMPLE2 now has Firebase Email/password in cmp-fajar and a normal MFA-audited
HRIS OIDC link. No mail sent, no real identity changed, temporary operator revoked.
Exact Mobile release34 logs in as Bima, selects the demo company, registers and
bootstraps chat on an OWNED persistent emulator. DB confirms one ACTIVE device and
one confirmed immutable keypublication at11:59:41. Product thread is open. No actual
phone send/decryption accepted yet: post-activation passive window had no traffic;
optional user request to reopen Bima is pending. Other three samples still need
activation. No phone UI driving/reset; original user's keys/outbox retained.

ACTIVE owned emulator5562/PID133701/startTicks155609766. Its persistent Mobile
`.work/demo-chat-device-2026-11-05/bima/userdata.img` and sidecars MUST survive;
start/stop scripts in that directory. Do not revoke its native parent or clear app
as test cleanup. Firebase plan and sample credentials are private ignored files;
never upload them. See Mobile `.work/chat-send-diagnosis-2026-11-05/CURRENT.md`.

Mobile forwarding WIP is safely stashed with untracked files at
bfdef2ce9641173e0ff68528f8982c5bab15bf72/basef6a26a1c. Core batch gate passed118;
forward metadata still incomplete/unvalidated. Apply after urgent fix; never pop
blindly. No local Gradle job; Mobile/Main source clean. Live11a/schema116 unchanged.
Continue long run, all54 groups open; push each commit and announce each install.

## Release34 queue fix delivered; forwarding continues — 5 November 2026

Mobile source745e5a0256904453992e5e864bdd59e373de9513 is installed in place as
ARM64 devRelease34 on10.77.77.3:5555. Hasheded7799bb9b643c1bcd5184581e2875444a4f74bda4bdf8b90336d876cdb699
matched directly on-device; signer/app ID/first-install time preserved. Announced
install-only,465.301s; no UI driving/reset. Two exact-APK emulator starts passed;
images reviewed, owned emulator stopped. Build/lint505.825s/zero lint issues.
[Mobile evidence](https://github.com/fajarxfce/hris-mobile/blob/f6a26a1c/docs/measurements/android-release-phone-2026-11-05-build34.json).

Actual original/new encrypted queue recovery native proof remains the prior165.743s
pass. Physical Bima old/new acceptance is still pending; user was asked once after
installation. Apple42017858911 on745e is building the simulator app after simulator
tests passed; not yet final acceptance. Live API/worker11a98115/schema116 unchanged.
No Main runtime source change or rollout is required for the queue fix.

Mobile WIP now adds atomic batch insertion for forwarding. Five shared SQLite/
IndexedDB cases plus existing suites pass118 fresh/86.668s/4,076 unchanged inputs.
Forwarded-content plumbing has started but is not validated/delivered. Continue
local recipient selection, durable one/multiple forwarding, indicator and native/
offline recovery; full Mobile gate and generic Starter port remain. Keep original
store/keys/intents. All54 PRD groups open, including edit/redaction, groups, calls
and meetings. Continue authorized long run, atomic pushes and announced installs.

## Queued-auth-recovery native acceptance passed — 5 November 2026

Mobile [745e5a02](https://github.com/fajarxfce/hris-mobile/commit/745e5a0256904453992e5e864bdd59e373de9513)
is pushed. Stable queue operation survives credential replacement; affected legacy
bindings require exact retained transactions in the same native family/device/room.
Bounded retry releases API/auth failures without bypassing trust checks. Empty chat
keeps the composer and removes manual network controls.
[Native contract](collaboration/sdk-queue-recovery.md),
[evidence](measurements/chat-queue-recovery-2026-11-05.json).

Both actual native suites pass165.743s/two cases/zero skips/12,281 unchanged inputs,
exact APK02269c9531c519579d334e25af2ea1caa58fb2cfa81f3e6a1ac284fb3961bb6e. The
one-shot403 leaves an original message parked; SDK auth replacement preserves keys
and queue ownership; a reproduced legacy record then permits a new message and
both decrypt at the peer with original transaction/receipt bindings. Server
assertions require exactly two accepted sends, one rejection and no premature
send. Earlier quote/process death/cold-offline native coverage remains passing.
Driver31 pass0.130s; emulator stopped1.604s with transport absence verified.

Mobile :check/Android/browser passed1,241 fresh cases; packaging alone failed,
then identical4,070 source inputs packaged/proof-tested55.536s/18 fresh. Focused54
fresh pass380.779s, domain43 earlier. Earlier failed attempts are recorded; three
current renders reviewed. Physical-phone Bima acceptance remains pending.

Main changes are test fixtures/docs only. Live API/worker11a98115/schema116 and
phone33 remain unchanged. Mobile clean signed release34 build/lint is now running;
then exact-APK emulator check and EACH phone install announced before install-only.
Apple CI42009694285 passed prior Mobile36e78206/release33 only. Group mirrorbf43351c
will be updated after paired commits. All54 PRD groups open; continue long run.

## Quoted backend live and current product FCM accepted — 5 November 2026

Live API/worker source11a98115 (bounded reply enveloped4f34140) replaces8b40 using
exact artifacts from all14 passed CI42004588608 jobs. Activation19.094s; API/worker/
provider healthy. Schema116/Matrix/TLS/data/config/dashboard preserved; retained old
images support image-only rollback. [Rollout](collaboration/quoted-reply-rollout.md),
[evidence](measurements/chat-quoted-rollout-2026-11-05.json).

Current Mobile36e78206 actual product FCM attempt2 passes241.839s/one case/zero skips;
driver179.364s, counts1/1/1. Real Firebase process restart/SystemUI encrypted inline
reply, five reviewed images and independent cleanup accepted. Worker bytes match CI.
[Evidence](measurements/product-chat-fcm-current-2026-11-05.json). Earlier typing timeout
retained;45s bounded input adjustment has no uncertain retries. No phone send proof.
Fresh empty chat still shows Load messages and needs UX correction.

Release33 source36e78206 is installed in place on10.77.77.3:5555 after announcement;
86.234s including pullback, exact e97aceff bytes matched, app ID/first-install preserved.
No UI launch or data reset. Connected phone has no running app process yet; optional
request to open Bima once is pending for new closed diagnostics. Preserve keys/intents.
Mobile Apple-only CI42009694285 on36e78206 is running. All54 groups open; continue full
long run, atomic pushes, group adoption/UI/edit/forward/delete-for-all/calls/meetings.

## Current product FCM setup attempt — 5 November 2026

Current Mobile36e78206 product/peer debug fixtures build116.057s; worker13.602s.
Actual product FCM attempt fails229.141s/one fresh case at ADB typing during login.
Login UI is visible; no crash marker, registration/pusher/send or valid screenshot
accepted. Driver and independent cleanup verify owned emulator/transport/image and
fixture credentials removed. [Evidence](measurements/product-chat-fcm-input-2026-11-05.json).
Only fixture typing gets45seconds and email/password stage markers; no uncertain
input retry.25 Python cases pass. Repeat actual scenario with the same artifacts.

Release33 ARM64 source36e78206 is ready: build522.126s/lint0, exact APK e97aceff,
88,337,740bytes, signer/Firebase/WireGuard/16KiB/backup/closedmarkers verified. Two
owned cold starts52.162s,12stable each and reviewed PNGs; emulator stopped. Phone
preflight32 same signer, still NOT installed. CI42004588608 has13/14 jobs passed;
await dashboard integration. Then Main work .work/chat-quoted-reply-2026-11-05/
stage-packages.py,stage-images.py,deploy-apps.py (prepared, NOT executed); API/worker
image-only update preserves Matrix/TLS/data. Announce EACH phone install before
install-only. Do not run Mobile release33 record.py before actual installation.

Old AND new Bima sends remain pending. New diagnostic release is needed to identify
the failing stage/retained SDK failure. Preserve keys/intents; no question pending.
All54 groups remain open. Continue long run and push every commit.

## Actual encrypted quoted replies verified — 5 November 2026

The actual recipient decrypts the third message with its exact first-event quote.
Kill-before-journal recovery retains the original transaction/reply intent; a later
cold process reads all three messages and the quote with the gateway unreachable.
Exactly three encrypted sends and all prior ownership/auth/history guards remain.
[Contract](collaboration/quoted-reply-native.md), [evidence](measurements/collaboration-quoted-native-2026-11-05.json).

Both native suites pass /159.069s/two cases/zero skips/12,277 unchanged inputs and
unchanged APK5c33de85. Driver31 pass. Mobile root/Android/browser/proof gate passes
2,155 fresh/417.551s/4,066 unchanged inputs. Owned emulator stopped. Applied native
stage drafts are NOT reusable. Mobile closed diagnostic markers are in6753b7a1.

NEXT: full CI42004588608 on Main11a98115 (production matches current test-only WIP),
validated API/worker-only envelope rollout retaining Matrix/TLS/data, clean release33
build/lint/emulator review and EACH install announced before install-only. Phone32
and live8b40/schema116 unchanged. Old AND new Bima sends still pending: read new closed
markers after delivery and fix the demonstrated cause; preserve keys/intents. No
question pending. All54 groups open; continue long run and push every commit.

## Mobile quoted reply UI and pending-send investigation — 5 November 2026

Mobile [f6f770bb](https://github.com/fajarxfce/hris-mobile/commit/f6f770bb7ea1dc815b46119055b82e4e90f558e7) is pushed.
Long-press Reply, composer/bubble quotes and checkpoint-driven local target previews
are implemented; closing a quote preserves draft text. Send has no spinner, and a
held connection does not block local acceptance or the next draft.
[Contract](https://github.com/fajarxfce/hris-mobile/blob/f6f770bb7ea1dc815b46119055b82e4e90f558e7/docs/collaboration-chat-quoted-replies.md),
[evidence](https://github.com/fajarxfce/hris-mobile/blob/f6f770bb7ea1dc815b46119055b82e4e90f558e7/docs/measurements/chat-quoted-replies-2026-11-05.json).

Final Mobile root/Android/browser data and presentation/format/architecture pass:
3,872 fresh cases, zero failures/errors/skips, 537.919s, 4,062 unchanged inputs.
Focused42 pass; interrupted ui3 wrapper/original successful child recovery and
subsequent normal root gate are recorded. Three reviewed renders remain hash-bound.
No new actual SDK encrypted quoted reply proof or phone install is claimed.

User confirms both OLD AND NEW Bima messages remain pending on phone32. Read-only
metadata shows active original parent/device/key publication and normal gateway
refreshes. Passive closed-category response samples show history/sync/join200 and
optional m.secret_storage.default_key403; neither that optional rejection nor an
older buffered ChatAuthSoftLogout proves the current send root cause. Mobile is
investigating original SDK queue failure/recovery. Preserve all keys and intents.
Actual native quoted reply/decryption/cold proof, validated envelope deployment and
next announced install-only remain pending. Live8b40/schema116 and phone32 unchanged.
Group runtime ports require their own validation. All54 PRD groups remain open;
continue the authorized long run, atomic pushes, group adoption/UI, calls and meetings.

## Mobile quoted-reply intent — 5 November 2026

Mobile [e47d9166](https://github.com/fajarxfce/hris-mobile/commit/e47d91662996a7fb724567ba7076938fc03d6724) is pushed.
Encrypted drafts/outbox retain the original reply reference through exact save/freeze/
queue retry, background recovery and paused native queue encoding. Complete content
fingerprints include the relation; same-text retries cannot retarget a logical send.
Outgoing reference validation matches Main gatewayd4f34140 without tightening historical
IDs. No quoted body snapshots. [Contract](https://github.com/fajarxfce/hris-mobile/blob/e47d91662996a7fb724567ba7076938fc03d6724/docs/collaboration-chat-quoted-draft.md),
[evidence](https://github.com/fajarxfce/hris-mobile/blob/e47d91662996a7fb724567ba7076938fc03d6724/docs/measurements/chat-quoted-draft-2026-11-05.json).

Final Mobile root/Android/browser/format/architecture gate501.591s/3,401 fresh/zero
failures/errors/skips/4,052 unchanged inputs. Earlier compile failure and passed
pre-alignment gate are recorded. Worker recovery uses a simulated SDK, not new actual
quoted-message interop. Product quote history/observer/UI is now WIP in Mobile, with
focused checks next. Then actual native encrypted reply and cold offline proof,
validated gateway deployment and product APK. Phone32 and live8b40/schema116 unchanged.
All54 groups remain open; continue long run and push every commit.

## Quoted reply gateway contract — 5 November 2026

Main accepts only the bounded `m.relates_to.m.in_reply_to.event_id` envelope. Plain
messages remain compatible; arbitrary relations and plaintext metadata are rejected.
Existing participant/native/key admission remains unchanged. Real Synapse preserves
the reference and original transaction across token rotation; native HTTP/PG checks
retain revocation. [Contract](collaboration/quoted-reply-envelope.md),
[evidence](measurements/collaboration-reply-envelope-2026-11-05.json).

Gate passes in 427.516s: 158 fresh cases, zero failures/errors, one existing opt-in
elapsed-retention worker case skipped, 8,211 unchanged inputs. Full worker suite ran.
No live deploy, no actual SDK quoted-message proof yet. Live8b40/schema116 and installed
Mobile32 remain unchanged. Mobile reply draft/outbox work is in progress, followed by
history and composer UI, actual encrypted native exchange and a validated phone update.
Group port requires its own gate. All54 acceptance groups remain open; continue long run.

## Cold offline history verified — 5 November 2026

Mobile [2032793a](https://github.com/fajarxfce/hris-mobile/commit/2032793a90f67c01eb2e4545c5b7e3437037b492) is pushed.

Actual encrypted canonical history remains readable after the third owned process
is killed, its gateway mapping removed, and a fourth process opens the original
store. Refused TCP connects before/after the read prove the server is unavailable.
Both earlier messages/event aliases match privately. No fourth encrypted send occurs.
This is native/repository evidence; full product UI and physical-phone internet-off
behavior are not newly claimed. [Contract](https://github.com/fajarxfce/hris-mobile/blob/2032793a90f67c01eb2e4545c5b7e3437037b492/docs/collaboration-chat-offline-restart.md),
[evidence](https://github.com/fajarxfce/hris-mobile/blob/2032793a90f67c01eb2e4545c5b7e3437037b492/docs/measurements/chat-offline-restart-2026-11-05.json).

Both actual SDK suites pass /155.541s/2 cases/12,261 unchanged inputs,
including original-key renewal/auth recovery, encrypted exchange, history, receipts
and process death. Mobile root/proof build passes /168.453s/18 fresh/4,051
unchanged inputs. Driver30 pass; initial mock-mapping failure retained. Owned emulator
is stopped. Only proof/test/driver source changes; phone32 remains installed from
Mobile64c9432f (deliverye347b0cb). Last sampled release32 process yielded no closed SDK
diagnostics; do not infer that the earlier Generic Bima transport error is solved.

NEXT: continue delivered edit/reply/forward/delete-for-all, original-bootstrap
resolution/adoption and group UI, calls and meetings. User's optional send-result
question is pending; preserve private keys and inspect closed diagnostics after
ordinary use. All54groups open, live8b40/schema116 unchanged. Push each commit and
continue long run. Port Main native fixture changes to Group only with its own gate.

## Product release 32 installed — 5 November 2026

Mobile delivery record [e347b0cb](https://github.com/fajarxfce/hris-mobile/commit/e347b0cbccec91f0ede7a82d230c484803558463) is pushed.

Latest validated product source [64c9432f](https://github.com/fajarxfce/hris-mobile/commit/64c9432f6538070f6adf750dd3920481efa7c032) is built as ARM64 devRelease 32
and installed in place on `10.77.77.3:5555`. Package `dev.fajar.hris.mobile.dev`,
version 1.0.0/code 32, non-debuggable. APK 88,304,880 bytes;
SHA-256 `104793ff434b71f7bd8b5c4c7ff313a8415d4be916474e72d8ca55d74139dcf5`. Existing development signer, app ID and first-install
time are preserved; pulled-back installed bytes match the verified artifact.
Installation was explicitly requested and announced; no phone UI launch, uninstall
or data clearing. [Evidence](https://github.com/fajarxfce/hris-mobile/blob/e347b0cbccec91f0ede7a82d230c484803558463/docs/measurements/android-release-phone-2026-11-05-build32.json).

Release assembly and lint pass in 500.117 s, with zero lint issues.
Signature, package/version, ARM64-only libraries, 16 KiB alignment, Firebase dev app,
WireGuard API and compiled backup exclusions are checked. Exact APK opens twice on
the owned emulator through its native bridge, with 12 stable seconds per start and
reviewed screenshots. The emulator is stopped. These are startup/install checks;
no new notification/SystemUI reply/FCM or full authenticated workflow proof is claimed.
Source header correction passed focused 18 fresh cases
and root/Android 812 fresh cases on identical
4,050 inputs. Earlier unchanged cases remain bound by report hashes.
No source changes occurred during release build.

The release removes connection status below the conversation name and the permanent
checkmark selection button. Long-press selects messages and reveals contextual
copy/delete actions. Original SDK authentication recovery retains local history and
replaces confirmed credentials in the original protected store without resetting keys.
[Header contract](https://github.com/fajarxfce/hris-mobile/blob/e347b0cbccec91f0ede7a82d230c484803558463/docs/collaboration-chat-header.md), [recovery contract](https://github.com/fajarxfce/hris-mobile/blob/e347b0cbccec91f0ede7a82d230c484803558463/docs/collaboration-chat-reconnect.md).

NEXT: observe closed operation diagnostics after ordinary phone use; earlier generic
Bima transport failures remain unattributed. User confirmed immediate history on
release31 reopening, but a physical internet-off test is not claimed. Preserve
original private SDK keys/account data. Continue delivered edit/reply/forward/delete
for everyone, original-bootstrap resolution/adoption, group runtime/UI, calls and
meetings. All54 groups open; live8b40/schema116 unchanged. Continue long run,
atomic pushes and validated delivery.

## Chat header correction — 5 November 2026

Mobile [64c9432f](https://github.com/fajarxfce/hris-mobile/commit/64c9432f6538070f6adf750dd3920481efa7c032) is pushed.

The thread header now shows the conversation name and Back only. Removed every
connection subtitle and the permanent checkmark selection button. Long-press selects
the first message; normal taps do not start selection. Contextual count/copy/delete
and accessible bubble selection remain. The empty-selection event/handler is removed.
[Contract](https://github.com/fajarxfce/hris-mobile/blob/64c9432f6538070f6adf750dd3920481efa7c032/docs/collaboration-chat-header.md), [evidence](https://github.com/fajarxfce/hris-mobile/blob/64c9432f6538070f6adf750dd3920481efa7c032/docs/measurements/chat-header-2026-11-05.json).

Focused18 / 89.943s and root/Android812 / 286.239s pass on 4,050 unchanged inputs. Reviewed screenshots include compact, landscape and 200% text.
User confirms immediate release31 Bima history on reopening. Physical internet-off
behavior is not yet tested on that phone; closed release31 Generic timeline/sync
errors still lack attribution. Native original-store recovery proof remains bound
to unchanged data source in8fdbe89b/Main0c62f0d7.

NEXT: clean-source release32 build/lint/owned-emulator startup, announce phone install
and use --install-only. Then inspect closed operation diagnostics, continue delivered
edit/reply/forward/delete-for-all, original-bootstrap resolution/adoption, group UI,
calls and meetings. All54 groups open; live8b40/schema116 unchanged. Push each commit
and continue the authorized long run. This HRIS feature does not need a Starter port.

## Actual original-gateway SDK recovery — 5 November 2026

Mobile [8fdbe89b](https://github.com/fajarxfce/hris-mobile/commit/8fdbe89bc9c97efc152c96367e24c0a0116f8fe8) is committed; matching root/native evidence and reviewed screenshots are included.

The real SDK receives typed rejected-refresh authentication, then the Mobile repository drains its exact original client and installs confirmed newer credentials into the same protected store. Original keys, HRIS parent/device and one key publication remain; actual SDK sync resumes. Both native suites pass on the same APK, preserving normal renewal, encrypted exchange/history/receipt/process-death assertions: 145.221s /2 cases/12,260 unchanged inputs, no skip. [Contract](collaboration/native-sdk-auth-recovery.md), [evidence](measurements/collaboration-sdk-auth-recovery-2026-11-05.json).

Mobile root/Android/browser/proof passes 2479 fresh/531.299s/4,050 unchanged inputs, with reviewed compact reconnect and large-text UI. User confirms release31 immediately shows Bima messages on reopening. Backend runtime remains live8b40/schema116. No production deployment or generic connection root-cause claim; release32 delivery is still next. Continue original-bootstrap resolution and organization adoption, delivered message actions, group runtime/UI, calls/meetings; all54 groups stay open. Push each atomic commit and keep Git handoffs current.

## Original Android SDK access renewal verified — 5 November 2026

The real pinned SDK renews both original gateway sessions after injected server-side
access expiry, retaining original keys, store, queued transaction and canonical history.
Subsequent process-death/receipt recovery and exactly three encrypted sends still pass.
Both refreshes cross the real HRIS API/PG/Synapse fixture; the server verifies ciphertext
with bounded refresh replay, without another rotation. Fixed server time keeps the
30-second verifier replay window open; this is not late-replay or clock-skew acceptance.
[Contract](collaboration/native-sdk-access-renewal.md).

Native case51.293s/fullgate123.990s, one passed and none skipped; format/architecture
pass on12,248 unchanged backend/Mobile inputs and exact proofAPK3cedee24. Mobile proof
build18host cases/168.282s/4,040 unchanged inputs. Only two backend test files change;
runtime source equals live8b40. Original-driver cleanup and emulator shutdown succeed.
[Evidence](measurements/collaboration-sdk-access-renewal-2026-11-05.json).

NEXT: read release31 operation diagnostics and user reopen result for the real Bima
error. Normal SDK renewal now passes; do not label that actual error fixed or reset
private keys/account data. Inspect typed rejected-auth signal and original gateway
recovery if indicated, then real delivered edit/reply/forward/delete-for-all, organization
adoption, group runtime/UI, calls and meetings. All54groups stay open, phone31 and
live8b40/schema116 unchanged. Continue long run, atomic pushes and validated delivery.

## Product release 31 installed — 5 November 2026

Mobile delivery record [8b13e8b9](https://github.com/fajarxfce/hris-mobile/commit/8b13e8b9ff851e7c1334de87891b2ec4c0678bb5) is pushed.

Latest validated product source [9d5ca633](https://github.com/fajarxfce/hris-mobile/commit/9d5ca633709f23588fd3fc9b29a88002bd4d3b4d) is built as ARM64 devRelease 31
and installed in place on `10.77.77.3:5555`. Package `dev.fajar.hris.mobile.dev`,
version 1.0.0/code 31, non-debuggable. APK 88,304,868 bytes;
SHA-256 `fec690c1031313b3353b73980e390477125c773d51fc103e3900e6c4b5f4f691`. Existing development signer, app ID and first-install
time are preserved; pulled-back installed bytes match the verified artifact.
Installation was explicitly requested and announced; no phone UI launch, uninstall
or data clearing. [Evidence](https://github.com/fajarxfce/hris-mobile/blob/8b13e8b9ff851e7c1334de87891b2ec4c0678bb5/docs/measurements/android-release-phone-2026-11-05-build31.json).

Release assembly and lint pass in 494.758 s, with zero lint issues.
Signature, package/version, ARM64-only libraries, 16 KiB alignment, Firebase dev app,
WireGuard API and compiled backup exclusions are checked. Exact APK opens twice on
the owned emulator through its native bridge, with 12 stable seconds per start and
reviewed screenshots. The emulator is stopped. These are startup/install checks;
no new notification/SystemUI reply/FCM or full authenticated workflow proof is claimed.
Source local-entry flow passed focused 193 fresh cases
and root/Android 805 fresh cases on identical
4,040 inputs. Earlier unchanged cases remain bound by report hashes.
No source changes occurred during release build.

The release includes local history before network setup, automatic foreground
reconnect, contextual unsent notification replies and shorter EN/ID copy. Routine
refresh and global Saved replies controls are removed. Long-press selection,
multiselect/copy and durable delete-for-me are also included since release30.
[Local-entry contract](https://github.com/fajarxfce/hris-mobile/blob/8b13e8b9ff851e7c1334de87891b2ec4c0678bb5/docs/collaboration-chat-local-entry.md) and
[selection contract](https://github.com/fajarxfce/hris-mobile/blob/8b13e8b9ff851e7c1334de87891b2ec4c0678bb5/docs/collaboration-message-selection.md).

NEXT: inspect operation-only Android diagnostics to resolve the actual Bima failure;
this delivery does not prove that provider/session problem fixed. Preserve original
private SDK keys and account data. Prove canonical-history recovery and SDK token
refresh on the isolated native fixture, then original-gateway recovery if evidence
requires it. Continue delivered edit/quoted reply/forward/delete-for-all, organization
bootstrap resolution, private-store inventory, hard-parent recovery and adoption,
group runtime/UI, typed push, calls and meetings. All54PRD groups remain open; live
backend8b40/schema116 unchanged. Continue long run, atomic pushes and validated delivery.

## Local chat entry, reconnect and unsent messages — 5 November 2026

Mobile [9d5ca633](https://github.com/fajarxfce/hris-mobile/commit/9d5ca633709f23588fd3fc9b29a88002bd4d3b4d) is pushed.

Chat now projects owned local history/outgoing messages before network preparation.
Reopening does not display a network loading ring. Foreground transient failures retry
with a timeout per attempt, increasing delay and lifecycle/account cancellation. Inbox and
thread routine refresh controls are removed; transient connection status stays in the
header, while local/known denial/recovery errors remain actionable. Owned SDK rows
already shown remain through transient failures; this does not newly persist a failed
canonical history import. [Contract](https://github.com/fajarxfce/hris-mobile/blob/9d5ca633709f23588fd3fc9b29a88002bd4d3b4d/docs/collaboration-chat-local-entry.md).

Held notification replies appear in the original conversation with preserved text and
a direct review action. Inbox rows identify unsent messages; only orphaned inputs use
a fallback entry. The global Saved replies utility no longer appears for ordinary
queues. Recovery still uses the original durable intent and draft-aware departure.
Short EN/ID copy and 375px light/dark200% plus 320px detail screenshots are reviewed.
Android diagnostics identify a closed SDK operation/type without content or secrets.

Final focused 193 fresh cases / 103.676 s (unchanged tests may reuse earlier passing outputs; report hashes distinguish fresh and reused results); root :check, Android-host and dev compilation 805 fresh cases / 265.682 s. Format, architecture and detekt pass. Both final gates preserve identical 4,040 inputs. [Evidence](https://github.com/fajarxfce/hris-mobile/blob/9d5ca633709f23588fd3fc9b29a88002bd4d3b4d/docs/measurements/chat-local-entry-2026-11-05.json).

NEXT: build and validate release31, announce then install-only on authorized phone,
and read the new operation diagnostics to resolve the actual Bima failure. Never reset
private SDK/account data. Upstream pinnedSDK26.10.23 Rust8de215a0 confirms automatic
refresh is enabled, /join always performs HTTP and refresh errors may become Generic;
ClientDelegate.didReceiveAuthError provides a typed rejected-token signal, currently
not installed by the product. Inspect callback ownership plus exact original gateway
recovery, and prove offline reopening after actual canonical acquisition. Do not claim
the reported session problem solved merely because generic error UI is removed.

Then continue delivered edit/reply/forward/delete-for-all, organization bootstrap
resolution/private-store inventory/hard-parent recovery/freeze/adoption and group
runtime/UI, typed push, calls and meetings. All54PRD groups remain open. No new APK,
phone interaction, live deployment, actual JNI/provider/FCM/SystemUI, Apple runtime or
performance acceptance in this commit. Phone30/source3e1cee4c and live8b40/schema116
unchanged. Continue long run and atomic pushes; no routine confirmation is needed.

## Chat selection and local deletion — 5 November 2026

Mobile [db446996](https://github.com/fajarxfce/hris-mobile/commit/db446996a54fd65e9fb18177334dcfb6c27ff164) is pushed.

Long-press selects a message; tapping adds more, with a contextual count/copy/delete
bar and Back-to-clear. Up to 50 selections survive local paging. Selection dismisses
the keyboard while preserving the durable composer draft. Single queued sends keep
their explicit cancellation; delete for me is a separate local choice. Clipboard
publication rechecks the original local owner and route generation.
[Contract](https://github.com/fajarxfce/hris-mobile/blob/db446996a54fd65e9fb18177334dcfb6c27ff164/docs/collaboration-message-selection.md).

Confirmed deletion atomically persists encrypted sender/event/logical visibility
rules alongside retained originals. Local queries omit hidden messages; later SDK
replay, history imports and aliases respect the choice. Original send evidence remains
available for reconciliation. Lost storage responses retry the same captured batch;
ordinary credential age does not hide owned facts, and account/access changes cancel
obsolete work. Ten SQLite plus ten IndexedDB, ten domain, eight ViewModel and four
Compose cases exercise these behaviors, plus the actual owning DI graph.

Focused 112 cases / 99.232 s; root :check, Android-host and dev compilation 3,058 fresh cases / 384.05 s; browser data/domain 1,654 fresh cases / 168.16 s. All pass on identical 4,033 build/application inputs; format, architecture and detekt pass. Four generated screenshots were opened and reviewed, including 200% text and a 320px confirmation. [Evidence](https://github.com/fajarxfce/hris-mobile/blob/db446996a54fd65e9fb18177334dcfb6c27ff164/docs/measurements/chat-message-selection-2026-11-05.json).

NEXT — newest user complaint takes priority before the next product APK: repeated
loading on reopening chat, generic failure in the Bima Santoso conversation, manual
refresh controls and technical Saved replies UX. Local canonical history observers
are network-independent, but acquisition/runtime failures still surface generic
retry UI and the foreground connection currently has no autonomous reconnect loop.
Notification Saved replies retains inline-reply recovery, not canned snippets;
move unresolved replies into their conversation context without losing original
text or uncertain-send evidence. Diagnose the actual error, not just hide it.

Read-only evidence on 5 November: installed app emitted Generic network/sync failure;
its WireGuard request to Matrix versions returned HTTP 200. Server services are
healthy. Linked Karyawan Demo gateway/device/native parent remain unrevoked and
active, while the short-lived gateway access is expired. That alone does not prove
a refresh defect. No credentials, SDK/private keys or account data were reset.

Then continue real delivered edit/quoted reply/forward/delete for everyone and the
existing organization bootstrap resolution, private-store inventory, hard-parent
recovery, freeze/adoption, group runtime/UI, typed push and calls/meetings work.
All 54 PRD groups remain open. Phone remains release30/source3e1cee4c and live backend
8b40/schema116; this slice is not installed yet. No JNI/provider/FCM/Apple runtime or
performance acceptance is claimed. Continue long run, atomic pushes and validated
install-only delivery with advance notice.

## Product release 30 installed — 5 November 2026

Mobile delivery record [ef9692b9](https://github.com/fajarxfce/hris-mobile/commit/ef9692b98b4437c30f2e2658537230b6648ce430) is pushed.

Latest validated product source [3e1cee4c](https://github.com/fajarxfce/hris-mobile/commit/3e1cee4c1c78d99d19c11dcd515cc7cfb69bf044) is built as ARM64 devRelease 30
and installed in place on `10.77.77.3:5555`. Package `dev.fajar.hris.mobile.dev`,
version 1.0.0/code 30, non-debuggable. APK 88,255,208 bytes;
SHA-256 `1fe1d23793d1e6c3ac2d28f5280ea5811a5e1ac23960109242574b8da00f759b`. Existing development signer, app ID and first-install
time are preserved; pulled-back installed bytes match the verified artifact.
Installation was explicitly requested and announced; no phone UI launch, uninstall
or data clearing. [Evidence](https://github.com/fajarxfce/hris-mobile/blob/ef9692b98b4437c30f2e2658537230b6648ce430/docs/measurements/android-release-phone-2026-11-05-build30.json).

Release assembly and lint pass in 534.843 s, with zero lint issues.
Signature, package/version, ARM64-only libraries, 16 KiB alignment, Firebase dev app,
WireGuard API and compiled backup exclusions are checked. Exact APK opens twice on
the owned emulator through its native bridge, with 12 stable seconds per start and
reviewed screenshots. The emulator is stopped. These are startup/install checks;
no new notification/SystemUI reply/FCM or full authenticated workflow proof is claimed.
Source owning setup flow already passed focused 76, root/Android 2,726 and browser
1,634 cases on identical 4,018 inputs. No source changes occurred during release build.

The release includes the accepted local observation and native organization setup
foundations since release 29; organization runtime/UI activation remains unfinished.
NEXT (latest user priority): WhatsApp/Telegram-style chat-room selection/actions,
including long-press, contextual toolbar, multi-select/delete and real edit/reply/
forward flows with offline retention and clear per-item outcomes. These UI changes
are not included in release30. Audit: `.work/chat-message-actions-2026-11-05/CURRENT.md`.
Then continue bootstrap operation resolution, private-store inventory, hard-parent
recovery, freeze/drain/handover and original SDK adoption, group UI/runtime and typed
push/background replies. Read-only protocol audit remains in group
`.work/space-bootstrap-resolution-2026-11-05/AUDIT.md`; no backend implementation or
live migration has started. All 54 PRD groups remain open; live
backend 8b40/schema 116 is unchanged. Continue long run and atomic pushes.

## Native organization setup owning flow — 5 November 2026

Mobile [3e1cee4c](https://github.com/fajarxfce/hris-mobile/commit/3e1cee4c1c78d99d19c11dcd515cc7cfb69bf044) is pushed.

The actual feature DI graph now owns finite explicit provisioning retry/bootstrap
preparation and replay, metadata-based retry reconciliation and credential-free local
queue observation. Original Registered LEGACY provenance/current selected identity
and native device anchor setup; no selected HR company or SDK reopening is required.
A missing SDK never authorizes SPACE fallback. [Contract](https://github.com/fajarxfce/hris-mobile/blob/3e1cee4c1c78d99d19c11dcd515cc7cfb69bf044/docs/collaboration-space-native-setup-flow.md).

Pending validates exact current resources and durably stores Unconfirmed before HTTP.
Uncertain retry retains the original operation/body/native parent without re-preparing
or reopening a frozen SDK. Bootstrap requires its original actual build; retry permits
same-family build upgrades. Original hard/wall/monotonic and mutex budgets are finite;
confirmed/concurrent denials survive failed storage and cancellation. Received gateway
credentials are encrypted before publication, including an access-expiry recheck after
storage. Completed credentials/receipts remain owned local facts across age/login/
parent replacement, never a live SDK/directory grant. The bounded local summary omits
tokens and drops queued old-account emissions on replacement/disposal.

Only a confirmed retry whose exact native device metadata has reached its recorded
provisioning revision can be archived. Lost archive responses recover the immutable
original receipt; another operation can then use the active slot. Pending/Unconfirmed/
Bootstrapped cannot be retired by this use case. No automatic retry is introduced.

Twenty-eight new common domain cases cover preparation (8), replay (13), retry
reconciliation (4) and local observation (3), including cancellation/late results,
original command recovery, concurrent denial, unchanged native/SDK provenance and
old-account emission disposal. Existing registration flows and setup transport/storage
are included as regressions. Actual AppContainer resolves the owning use cases and
single replay instance. Focused: 76 cases / 131.109 s.
Root :check, Android-host and dev compilation: 2,726 fresh cases /
340.396 s. Browser data/domain: 1,634 fresh cases /
141.395 s. Final gates preserve identical 4,018 build/application
inputs; format, architecture and detekt pass. All gate sessions closed.
[Evidence](https://github.com/fajarxfce/hris-mobile/blob/3e1cee4c1c78d99d19c11dcd515cc7cfb69bf044/docs/measurements/collaboration-space-native-setup-flow-2026-11-05.json).

NEXT: explicit bootstrap operation recovery beyond 30 s, actual-build changes or an
advanced gateway head. Null gateway metadata cannot exclude a late original request;
preserve original/replacement intent through crashes, never forge headers or discard
uncertainty. Then retained private-store inventory, explicit hard-parent recovery,
old-runtime freeze/drain/handover and original-store adoption, group runtime/UI and
typed push/background replies. No product route was enabled by this DI-bound slice.

All 54 PRD groups remain open. No new APK/phone, live deployment, provider/FCM,
JNI/private-key proof, Apple runtime or performance acceptance. Live backend
8b40/schema 116 and phone release 29/source 084662c5 unchanged. Continue long run,
atomic pushes and validated install-only product delivery with advance notice.

## Durable native organization setup storage — 5 November 2026

Mobile [28377007](https://github.com/fajarxfce/hris-mobile/commit/28377007d6023ae4c0a6ab43c809deaddb96ea9e) is pushed.

Native retry/bootstrap intent now retains the original registration reference,
operation/native parent, platform/build and exact request in encrypted account/space
storage. Local login/company replacement and ordinary age do not hide owned work.
Unconfirmed precedes HTTP; terminal Retried or Bootstrapped preserves the exact device,
provisioning/gateway version and original provider identity. Received group gateway
tokens are encrypted and redacted. [Contract](https://github.com/fajarxfce/hris-mobile/blob/28377007d6023ae4c0a6ab43c809deaddb96ea9e/docs/collaboration-space-native-setup-storage.md).

One active resource per original native parent has bounded account observation and
exact CAS. Shared-key loss cannot regenerate over active/inactive/archive records.
After the owning caller reconciles a confirmed retry against current metadata,
archiveRetried preserves its exact request/outcome as immutable evidence and releases
the active slot. Lost archive responses and same-operation insertion recover the
original completion without publishing pending work. Exact-ID archive/evidence checks
reject substitutions. Bootstrap retirement/adoption remains a separate owning flow.

Fifteen common storage cases pass on actual SQLite and IndexedDB, covering encrypted
gateway restart/new login, original intent, lost commit responses, contention/CAS,
terminal outcomes, native/account isolation, wrong provider/token/bounds, inactive
archived key loss, immutable archives, retained denial and bounded observation.
Actual AppContainer resolves the owning repository. Focused: 41 cases /
119.196 s. Root :check, Android-host and dev compilation: 2,698
fresh cases / 339.227 s. Browser data/domain: 1,606 fresh cases /
138.797 s. Final gates preserve identical 4,006 build/application
inputs; format, architecture and detekt pass. All gate sessions closed.
[Evidence](https://github.com/fajarxfce/hris-mobile/blob/28377007d6023ae4c0a6ab43c809deaddb96ea9e/docs/measurements/collaboration-space-native-setup-storage-2026-11-05.json).

NEXT: finite explicit preparation/replay and retry receipt reconciliation, using the
original Registered anchor/current identity/status, durable uncertainty before HTTP
and gateway storage before publication. Enforce current admission and original hard,
wall and monotonic deadlines. Preserve confirmed denial through failed storage and
cancellation. Parent/build replacement cannot rewrite uncertain bootstrap. Null gateway
metadata cannot disprove a late old operation; explicit server operation recovery or
supersession must preserve old and replacement intent beyond the 30 s replay window. Then
retained private-store inventory, explicit hard-parent recovery, old-runtime freeze/
drain/handover and original-store adoption, group runtime/UI and typed push/replies.

No owning lifecycle flow or product route is activated by storage alone. All 54 PRD
groups remain open. No new APK/phone, live deployment, provider/FCM, JNI/private-key
proof, Apple runtime or performance acceptance. Backend 8b40/schema 116 and phone
release 29/source 084662c5 unchanged. Continue long run and atomic pushes.

## Native organization retry/bootstrap transport — 5 November 2026

Mobile [0c8b05e4](https://github.com/fajarxfce/hris-mobile/commit/0c8b05e4b91d1f01272fb962073f552ac4a5dab5) is pushed.

Native setup now has typed retry/bootstrap requests, a redacted group gateway result,
raw HTTP/source adapters and a mapping repository in the actual owning DI graph.
Both endpoints use exact native session/operation and no company/cookie transport.
Retry validates the exact device and provisioning revision + 1 receipt. Bootstrap
requires original account/space/device/native parent, expected provider USER/DEVICE,
gateway version, group-only token namespace and supported dates. Current default
headers remain intact: retry permits build changes while bootstrap requires its
original actual build/platform. [Contract](https://github.com/fajarxfce/hris-mobile/blob/0c8b05e4b91d1f01272fb962073f552ac4a5dab5/docs/collaboration-space-native-setup.md).

User and device origins stay independent; typed SPACE transport is not enrollment
eligibility. No endpoint is derived from provider metadata. Expired gateway metadata
remains typed; current usability/native hard expiry and durable credential storage
belong to the owning use case. Pending and late cancelled responses cannot publish.

Twelve new common transport cases cover original native/body/operation identity,
actual Android/iOS headers, bootstrap build binding, independently selected user and
device origins, bounded/malformed responses, group token namespace, classified denial,
pending cancellation and noncooperative late response disposal. Actual AppContainer
resolves the new native setup repository. Focused: 26 cases /
96.609 s. Root :check, Android-host and dev compilation: 2,683
fresh cases / 343.862 s. Browser data/domain: 1,591 fresh cases /
136.713 s. Final gates preserve identical 3,998 build/application
inputs; format, architecture and detekt pass. All gate sessions closed.
[Evidence](https://github.com/fajarxfce/hris-mobile/blob/0c8b05e4b91d1f01272fb962073f552ac4a5dab5/docs/measurements/collaboration-space-native-setup-2026-11-05.json).

NEXT: durable encrypted account/space retry/bootstrap commands and finite explicit
preparation/replay using the original registered anchor, durable uncertainty before
HTTP and received credentials before publication. Preserve confirmed denials through
failed persistence/cancellation. Old-build uncertain bootstrap needs explicit current
metadata reconciliation, never forged headers or an edited operation. Then retained
private-store inventory, hard-parent recovery, freeze/drain/handover/adoption of the
original SDK store, current group runtime/UI and typed push/background replies.

No durable lifecycle command or product route is activated in this transport slice.
All 54 groups remain open. No new APK/phone, live deployment, provider/FCM, JNI/private
key proof, Apple runtime or performance acceptance. Backend 8b40/schema 116 and phone
release 29/source 084662c5 unchanged. Continue long run and atomic pushes.

## Native original-device registration flow — 5 November 2026

Mobile [46db17ee](https://github.com/fajarxfce/hris-mobile/commit/46db17ee308ffab4c8d5c7744af12f840b2e2c48) is pushed.

The owning feature now binds account-owned registration observation, finite explicit
LEGACY preparation and serialized replay. Preparation reads selected identity and
native-device metadata and borrows the already open original SDK; it retains exact
provider/company/device/fingerprint/physical parent before any registration HTTP.
No selected HR workspace is required. Missing metadata/SDK never permits SPACE
fallback or a rewritten physical namespace. [Contract](https://github.com/fajarxfce/hris-mobile/blob/46db17ee308ffab4c8d5c7744af12f840b2e2c48/docs/collaboration-space-registration-flow.md).

Pending replay checks actual original SDK identity, stores Unconfirmed before HTTP,
and reuses the immutable native operation/body after uncertain completion without
reopening a possibly frozen SDK. Actual current platform/build headers are sent;
register permits a build upgrade in the same native family. A fresh native parent
cannot replay the old device operation. The fixed 15 s budget includes mutex/local
work; original hard expiry, wall rollback, monotonic expiry and ownership are checked
after suspension. Confirmed and concurrent directory denials survive failed retries
and cancellation. A retained Registered receipt is an account-owned fact, never SDK
or directory authority. Old-account observer emissions are cancelled on replacement.

Twenty-eight new cases cover preparation (8), replay (10), local observation (3)
and native transport (7), including cancellation, late results, loss after commit,
account/parent replacement, concurrent denial, original SDK provenance and exact
request replay. Actual AppContainer resolves the observer/preparer and replay
singleton. Focused: 62 cases / 132.36 s. Root :check,
Android-host and dev compilation: 2,659 fresh cases / 331.944 s.
Browser data/domain: 1,579 cases / 136.27 s. All final gates
preserve identical 3,989 build/application inputs; format, architecture
and detekt pass. All gate sessions closed.
[Evidence](https://github.com/fajarxfce/hris-mobile/blob/46db17ee308ffab4c8d5c7744af12f840b2e2c48/docs/measurements/collaboration-space-registration-flow-2026-11-05.json).

NEXT: native provisioning retry/bootstrap using original registration anchors and
current metadata, then retained private-store inventory, explicit hard-parent recovery,
original-store handover/drain/adoption, group runtime/UI and typed push/background
replies. SPACE live replay remains unavailable until inventory/recovery establishes
eligibility. No new caller/product route has been activated by this DI-bound slice.

All 54 PRD groups remain open. No new APK/phone, live deployment, provider/FCM,
JNI/private-key proof, Apple runtime or performance acceptance. Live backend
8b40/schema 116 and phone release 29/source 084662c5 unchanged. Continue long run,
atomic pushes and validated install-only product delivery with advance notice.

## Durable organization device registration — 5 November 2026

Mobile [024bc193](https://github.com/fajarxfce/hris-mobile/commit/024bc19376123beb6da8d1bee6d9838426361844) is pushed.

Encrypted registration storage now retains original operation/native binding,
platform/build, selected provider user, label/device origin and exact original SDK
company/user/device/fingerprint/physical native family. User origin and device origin
are independent: a new SPACE device can use an existing LEGACY user, but storage
never authorizes that enrollment. The original physical family remains separate
from the request parent; no cross-parent recovery authority is inferred.
[Contract](https://github.com/fajarxfce/hris-mobile/blob/024bc19376123beb6da8d1bee6d9838426361844/docs/collaboration-space-registration-storage.md).

One registration anchor per original request parent supports bounded all-parent
account observation after reauthentication and exact-parent reads. Empty reads
allocate no key. Separate AES-GCM request/outcome purposes bind account/space/native
resource/op/revision; protected key loss cannot regenerate over retained records.
Unconfirmed is durable, CAS checks the exact original, and Registered is terminal.
Confirmed access failures cannot be downgraded by uncertain retries. No HTTP, SDK,
scheduling, server schema or directory admission change is introduced.

Fourteen common storage cases pass on actual SQLite and IndexedDB. They cover
restart/new login, immutable original operation/provenance, response loss after
commit, contention/CAS, terminal success, corrupt routing/ciphertext/bounds, shared
key loss, independent user/device origins, retained denial and all-parent inventory.
Actual AppContainer resolves the repository. Focused: 14 cases /
118.582 s. Root :check, Android-host and dev compilation: 2,624
fresh cases / 350.224 s. Browser data/domain: 1,551 cases /
135.925 s. All final gates preserve identical 3,980 build/application
inputs; format, architecture and detekt pass. All gate sessions closed.
[Evidence](https://github.com/fajarxfce/hris-mobile/blob/024bc19376123beb6da8d1bee6d9838426361844/docs/measurements/collaboration-space-registration-storage-2026-11-05.json).

NEXT: account-owned local observer plus finite explicit registration preparation
and replay/HTTP. Reacquire current selected user/native status and actual original
SDK under one bounded attempt, independently of retained command age. Use the
original operation/native parent and actual current platform/build headers; do not
copy identity-selection's fresh-parent retry rule. Register/retry do not bind old
build; bootstrap does. No SPACE fallback on an absent open SDK or current-parent
device: retained-store inventory or explicit recovery must establish eligibility.
Hard-parent recovery, original physical-store adoption, current group runtime/UI
and typed push/background replies remain outstanding. Completed anchors stay
retained; later archival must preserve the original registration for recovery.

All 54 PRD groups remain open. No new APK/phone, live deployment, provider/FCM,
Apple runtime or performance acceptance. Live backend 8b40/schema 116 and phone
release 29/source 084662c5 unchanged. Continue long run, atomic pushes and validated
install-only product delivery with advance notice.

## Selected organization identity on native clients — 5 November 2026

Mobile [c79ca453](https://github.com/fajarxfce/hris-mobile/commit/c79ca4532e42ff4c8d2c1856ddd077c91b75d539) is pushed.

Bodyless native GET now reads the already selected account/space provider user.
The typed 4 KiB projection validates canonical legacy/new identity, original company
provenance and immutable version, while all HTTP stays on the configured HRIS
endpoint. Raw port 0 is rejected before URL-library normalization. Null remains an
absent selection and never runs POST selection or implies an empty original store.
The actual owning feature DI graph resolves this read path.
[Contract](https://github.com/fajarxfce/hris-mobile/blob/c79ca4532e42ff4c8d2c1856ddd077c91b75d539/docs/collaboration-space-identity-observation.md).

Identity and device status now share an internal finite read policy function, not
use-case calls. The original native hard deadline and 15 s wall/monotonic budget
cover local admission and HTTP. Current ownership is checked before/after acquisition
and final admission; cancelled/late/replaced-account results cannot publish.
Confirmed denial survives cancellation and failed restriction persistence; metadata
success never clears a denied directory, and a newer concurrent denial wins.
Seven new common HTTP cases and seven new domain cases pass, alongside 13 device
lifetime and 20 identity-selection regressions. Actual AppContainer resolves the new
reader. Final focused gate: 47 fresh cases / 126.165 s.
Root :check, Android-host and dev compilation: 2,610 fresh cases /
327.498 s. Browser data/domain: 1,537 cases / 133.924 s.
All final gates preserve identical 3,972 build/application inputs;
format, architecture and detekt pass. All gate sessions closed.
[Evidence](https://github.com/fajarxfce/hris-mobile/blob/c79ca4532e42ff4c8d2c1856ddd077c91b75d539/docs/measurements/collaboration-space-identity-observation-2026-11-05.json).

NEXT: durable native device registration/retry/bootstrap, using the selected Matrix
USER and actual retained SDK identity from Mobile 5860290e. Preferred legacy DEVICE
is not necessarily this phone. Retain immutable operation, native-parent, platform,
realm, original physical owner/user/device/fingerprint. Registration/retry bind the
original native parent but not old client build; bootstrap binds actual build too.
Identity-selection retry rules therefore cannot be copied unchanged. User/device
origins are independent: a new device may use an existing legacy provider user.
Missing current native-device status is not proof that no older SDK exists. Retained-store inventory,
hard-parent recovery, group runtime ownership/UI and typed push/background replies
remain unfinished. No spaceId/companyId or old-store native-family substitution.

Backend 316cd708 has the matching endpoint. No product UI change or new APK installation,
live deployment, provider/FCM, Apple runtime or performance acceptance. All 54 PRD groups
remain open. Live backend 8b40/schema 116 and installed phone 29/source 084662c5 unchanged.
Continue long run, atomic pushes and validated install-only product delivery.

## Native selected provider-user metadata — 5 November 2026

Group [316cd708](https://github.com/fajarxfce/hris-platform/commit/316cd7083bc154e2e28b9425349ed63d40d6425f) is pushed.

GET `/api/v1/collaboration/spaces/{spaceId}/identity` now returns only the already
retained account/space provider-user intent. Missing selection stays null even
when an eligible legacy preference exists. No selection/device/provisioning,
receipt/audit/outbox or provider/credential operation is triggered. The private,
no-store projection contains eight metadata fields and omits the preferred device,
keys and tokens. [Contract](https://github.com/fajarxfce/hris-platform/blob/316cd7083bc154e2e28b9425349ed63d40d6425f/docs/collaboration/space-identity-read.md).

Shared account guard, exact native parent, current group/client/security admission,
original30s wall budget, existing transaction/query timeouts and interruption
checks fence the read. Admission is rechecked after either snapshot outcome;
committed offboarding or a new MFA requirement cannot publish old metadata.
Five new PostgreSQL cases and one new HTTP case pass, with the existing native
transport guard extended to GET identity. Focused domain/PG: 16 cases /
55.207 s; actual API context: 18 cases / 105.251 s.
Full worker: 137 passed, 1 existing opt-in elapsed-retention skip /
375.238 s. All final gates preserve the same 8,506 Git-enumerated
build/application inputs; format and architecture pass. All sessions closed.
[Evidence](https://github.com/fajarxfce/hris-platform/blob/316cd7083bc154e2e28b9425349ed63d40d6425f/docs/measurements/collaboration-space-identity-read-2026-11-05.json).

NEXT: Mobile finite native transport/use case for this endpoint, then durable
group registration/retry/bootstrap retaining actual old SDK provenance. Match the
selected Matrix USER to the actual local SDK user; a preferred DEVICE may belong
to another phone using that same user. Existing Mobile5860290e provides the local
SDK identity port. Current workspace alone cannot choose the correct original user.
No spaceId/companyId substitution, implicit new SPACE store, frozen-runtime reopen
or native-family substitution in the old physical SDK hash. Closed-store inventory,
hard-parent recovery, current group SDK ownership, global UI and typed push/replies
remain separate unfinished work.

No SQL schema/role change or live deployment. Current product remains company chat;
all54 PRD groups open. Live backend8b40/schema116 and installed phone29/source084662c5
are unchanged. No new provider/FCM, phone, Apple runtime or performance acceptance.
Continue long run, atomic pushes and validated install-only product delivery.

## Local SDK identity observation — 5 November 2026

Mobile [5860290e](https://github.com/fajarxfce/hris-mobile/commit/5860290e69b26c85f049467363ec55170b3e72e7) is pushed.

A narrow local-identity port now aliases the existing application Android runtime.
It borrows child ownership to read public SDK user/device/key identity, and preserves
cancellation, directory lifetime and late credential failures. An absent open client
performs no credential/store/network I/O and cannot imply an empty retained-store
inventory. Unsupported hosts remain explicitly unavailable. No implicit restore,
registration, private-key export, handover or physical namespace change occurs.
[Contract](https://github.com/fajarxfce/hris-mobile/blob/5860290e69b26c85f049467363ec55170b3e72e7/docs/collaboration-local-sdk-identity.md).

Five new Android-host cases cover absent-runtime I/O, owned public-key observation,
invalid/failed clients, pending cancellation/resource release and concurrent credential
failure. Existing Android handover, background and publication regressions also pass.
These use inert FFI handles, not JNI/provider proof. Real AppContainer resolves the
port to the same runtime instance without starting observation. Final root :check,
Android-host and Android dev compilation pass 350.063 s / 2,589 fresh
cases; browser data/domain pass 134.092 s / 1,523 cases. Both retain
identical 3,964 source inputs; format, architecture and detekt pass. All
gate sessions are closed. [Evidence](https://github.com/fajarxfce/hris-mobile/blob/5860290e69b26c85f049467363ec55170b3e72e7/docs/measurements/collaboration-local-sdk-identity-2026-11-05.json).

NEXT: expose/read already selected provider-user intent through a bounded native
metadata endpoint, without selecting or enrolling on GET. Current selection receipt
only identifies account/version; selected provider user may come from another HR
company. Match actual local SDK Matrix user, not merely current workspace or the
identity preference's original device ID (multiple devices may share that user).
Then use this port inside the owning durable group registration preparation,
retaining exact original company/device/user/key provenance after finite current
account/native admission checks. The port itself does not grant that admission or
prove private-key possession. Retain original physical owner, whose namespace
includes original native family; keep current authority separate. Closed-store
inspection, hard-parent recovery, new-SPACE retained-store inventory and group SDK
adoption still need explicit owners. Continue native registration/retry/bootstrap,
global UI and typed group push/background/replies. No companyId/spaceId substitution.

Current product remains company chat, all54 PRD groups open. Live backend8b40/schema116
and installed phone release29/source084662c5 are unchanged. No new phone installation,
UI interaction or provider/FCM operation. Continue long run, atomic pushes and
validated install-only product updates with advance notice.

## Native organization device status — 5 November 2026

Mobile [fb055f63](https://github.com/fajarxfce/hris-mobile/commit/fb055f630570ada53a93020f3932308b2df48a72) is pushed.

One explicit group native-device GET now maps bounded public status separately
from device/SDK authority. It validates origin, versions, provisioning/outcome
consistency and gateway timestamps; expired metadata remains metadata. Fresh
login may return null while old keys still exist. No enrollment, polling, SDK
acquisition, credential bootstrap or directory admission clearing is triggered.
[Contract](https://github.com/fajarxfce/hris-mobile/blob/fb055f630570ada53a93020f3932308b2df48a72/docs/collaboration-space-device-status.md).

The owning use case checks exact current native/local ownership across pending
boundaries, clips the original hard/wall/monotonic lifetime, retains received
access/build denial through cancellation or failed storage, and rejects a newer
concurrent directory denial. Successful recovery metadata cannot reopen the gate.
Seven native HTTP and thirteen domain lifecycle cases pass. Actual AppContainer
resolves the repository and owning use case. Final root :check, Android-host and
Android dev compilation pass 342.801 s / 2,584 fresh cases; browser
data/domain pass 136.437 s / 1,523 cases. Both preserve identical
3,962 source inputs; format, architecture and detekt pass. All gate sessions
are closed. [Evidence](https://github.com/fajarxfce/hris-mobile/blob/fb055f630570ada53a93020f3932308b2df48a72/docs/measurements/collaboration-space-device-status-2026-11-05.json).

NEXT: durable native group registration/retry/bootstrap commands and recovery,
original SDK-store adoption/current authority, explicit native hard-parent recovery,
global UI and typed push/background/replies. Server registration/retry fingerprints
include original native parent but not client build; bootstrap includes native
parent, versions and actual build. Preserve original provenance and actual client
headers; neither copy account-wide identity replay into device operations nor
apply bootstrap build restrictions to every command. Null status is not permission
to create a replacement SPACE device. Retained original Matrix user/device/private
keys still require explicit continuity verification; public fingerprints alone
are not proof. The physical SDK namespace includes original native family; changing
it selects another store. Keep immutable physical owner separate from current
admission, and establish retained-store inventory before enabling new SPACE enrollment.
No backend behavior was changed by this status increment.

Current product remains company chat; all 54 PRD groups stay open. Live backend
8b40/schema116 unchanged. Phone release29 remains installed from source084662c5,
without this increment. No new phone installation/UI interaction. Continue long
run, atomic pushes and validated install-only product delivery with advance notice.

## Durable organization identity selection — 5 November 2026

Mobile [4123cfc1](https://github.com/fajarxfce/hris-mobile/commit/4123cfc1365e94ac334c62041db8eb0f39fa1527) is pushed.

Native group identity selection now retains an encrypted account/space command
before HTTP, preserves its exact operation across response loss and process/login
recovery, and stores a confirmed account/version receipt. Local command facts stay
readable offline; queued old-account emissions are cancelled. Explicit attempts
persist Unconfirmed before transport, serialize finite work and keep received
access denials gated even at cancellation or failed storage. Selection success
never clears directory admission. [Contract](https://github.com/fajarxfce/hris-mobile/blob/4123cfc1365e94ac334c62041db8eb0f39fa1527/docs/collaboration-space-identity-command.md),
[evidence](https://github.com/fajarxfce/hris-mobile/blob/4123cfc1365e94ac334c62041db8eb0f39fa1527/docs/measurements/collaboration-space-identity-command-2026-11-05.json).

Review confirmed the server operation fingerprint is account/space/provider realm,
without the first native family or build. An explicit retry therefore uses its
freshly supplied native owner and actual client headers while retaining immutable
original provenance. A real new login has a new local session ID; stale supplied
owners and changes during an attempt still fail. This selection rule does not
change device registration/bootstrap or authorize retained SDK-store adoption.

Nine shared SQLite/IndexedDB, six HTTP and fourteen domain cases pass; nine root
lifetime regressions remain green. Actual app DI resolves repositories/use cases
and singleton replay. Final root :check, Android-host and Android dev compilation
pass 124.829 s / 1,156 fresh cases; browser data/domain pass
150.292 s / 1,503 cases. Final 3,950 inputs are identical;
format, architecture and detekt pass. All gate sessions closed. Earlier compilation
fixes and the replay-contract review remain recorded separately.

NEXT: native group device status and durable registration/retry/bootstrap commands,
original SDK-store adoption and explicit native hard-parent recovery, group UI and
typed push/background/replies. Selection receipt identifies only account/version;
verify actual original Matrix user/device/key continuity before adopting a store.
Never substitute a space UUID for companyId, silently register a replacement SPACE
device when old keys exist, or reopen the frozen old runtime. Device/bootstrap
idempotency and immutable physical provenance need their own server-contract review;
do not copy selection's broader retry rule into them. No user/SDK/device is created
by observing the saved receipt.

Product still uses company chat and all 54 PRD groups remain open. Live backend
8b40/schema 116 is unchanged. Phone release 29 is installed-only and byte/signer/UID
verified from earlier product source 084662c5; this identity increment is not in
that artifact. No phone UI was opened and no data was cleared. Continue long run,
updates, atomic pushes and validated install-only product delivery.

## Product release 29 installed on the authorized phone — 5 November 2026

Mobile [e068244b](https://github.com/fajarxfce/hris-mobile/commit/e068244bb2dfd606425281122e04b4966c6f9193) is pushed.

WireGuard ADB became reachable. Existing release 28 and the development signer
were verified before an explicit install-only update to release 29. Pulled-back
APK bytes match SHA-256 `e17f1681dcc5cacce43fb126b53da261e5b89423e65c3ab3737d29186c46dca1`;
package `dev.fajar.hris.mobile.dev`, version 1.0.0/29, non-debuggable. UID 10962
and original first-install identity are preserved. No uninstall, data clearing,
phone UI launch or interaction occurred. Installation and verification completed
in 81.256 seconds. [Evidence](https://github.com/fajarxfce/hris-mobile/blob/e068244bb2dfd606425281122e04b4966c6f9193/docs/measurements/android-release-phone-2026-11-04-build29.json).

This delivers the previously verified product artifact from 084662c5; its emulator
startup/notification evidence remains as recorded. Later organization-root and
identity-command work is separate. No new FCM, group runtime, Apple, call/meeting
or authenticated phone workflow acceptance. All 54 PRD groups remain open.

NEXT: continue durable native organization identity/device commands, original SDK
adoption/hard-parent recovery, group UI and typed push/background/replies. Current
identity-command work is uncommitted: 24 Kotlin files, encrypted account/space
CommandStore state, explicit prepare/replay, original native/build binding and
shared denial retention. Nine SQLite cases and five HTTP cases passed; domain
assertion compilation was corrected and focused3 is running. Full/browser gates
and source review remain required; do not call this identity acceptance. State
and gate ownership are in `.work/space-identity-2026-11-05/CURRENT.md`. Preserve
that source work while recording this independent phone-delivery checkpoint.
Continue long run, updates, atomic pushes and validated install-only updates.

## Account-owned organization cache root — 5 November 2026

Mobile [c4f79149](https://github.com/fajarxfce/hris-mobile/commit/c4f791492971113bcb12f5047cc639a3b51f58c4) is pushed.

The organization cache locator now survives restart and reauthentication for the
same account without depending on an HR company or native credential age. Its
first publication is encrypted and atomic; replay retains the same ciphertext,
and a different organization cannot replace it silently. A refused write is
reconciled against actual storage before claiming migration. Root-only ciphertext
also fences lost-key replacement. [Contract](https://github.com/fajarxfce/hris-mobile/blob/c4f791492971113bcb12f5047cc639a3b51f58c4/docs/collaboration-space-root.md),
[evidence](https://github.com/fajarxfce/hris-mobile/blob/c4f791492971113bcb12f5047cc639a3b51f58c4/docs/measurements/collaboration-space-root-2026-11-05.json).

Nine shared SQLite/IndexedDB storage cases and nine domain cases pass. Real
application DI resolves the repository and both owning use cases. Final root
:check, Android-host and Android dev compile pass 404.541 s /
2,522 fresh cases; browser data/domain pass 148.906 s /
1,474 cases. Both preserve the same 3,929 source inputs;
format, architecture and detekt pass. All gate sessions closed. The initial import
failure and later refused-write review correction remain in the evidence.

NEXT: durable native group identity/device setup commands, SDK-store adoption and
explicit hard-parent recovery, organization UI and typed group push/background/
replies. Identity selection must precede device registration. Its receipt identifies
the account/version, not the selected Matrix user: verify original provider user,
device and key fingerprint before adopting any retained SDK store. Stable operation
identity, build/platform and uncertainty must survive interrupted delivery. Never
substitute spaceId for companyId or open old runtime after freeze. New sign-ins
create a fresh local session ID; token refresh keeps the exact native binding.

The locator alone activates no group UI, network mutation or provider/device runtime.
Product still uses company chat; all 54 PRD groups remain open. Live backend
8b40/schema 116 is unchanged. Release 29 remains verified but uninstalled; four
15-second phone connection attempts timed out and the availability question is
pending. Release 28 is the last confirmed phone install. Continue long run, updates,
atomic pushes and validated install-only product delivery without driving phone UI.

## Current company-chat list cancels queued old results — 4 November 2026

Mobile [0519e0d0](https://github.com/fajarxfce/hris-mobile/commit/0519e0d071a835438854929de1930a59dfc3c368) is pushed.

A slow consumer could receive an old page result after page selection or read
admission changed. The existing product observer now resolves guarded access and
selection before one final cancellable row subscription. Both page and denial
replacement discard the old pending row; disposal leaves no local collectors,
and selection still starts no network request. Company/account/age policies stay
intact. [Contract](https://github.com/fajarxfce/hris-mobile/blob/0519e0d071a835438854929de1930a59dfc3c368/docs/collaboration-local-list.md),
[evidence](https://github.com/fajarxfce/hris-mobile/blob/0519e0d071a835438854929de1930a59dfc3c368/docs/measurements/collaboration-list-observation-2026-11-04.json).

The unchanged product observer failed the regression with three deliveries instead
of two. The corrected observer passes both replacement variants. Focused tests,
format/architecture/detekt pass. Root :check, Android-host and Android dev compilation
pass 335.456 s / 2,504 fresh cases; browser domain
pass 78.347 s / 969 cases. Final inputs identical
across all gates (3,918 files). All sessions closed. No new native/provider/FCM/
Apple or physical-device acceptance.

NEXT: durable group identity/device setup commands, SDK store adoption and explicit
hard-parent recovery, organization UI and typed group push/background/replies.
A durable account-root discovery record is also needed for startup to open the
organization directory offline; never infer it from arbitrary cached partitions.
Mobile transport trace is in `.work/space-directory-usecases-2026-11-04/next-device-transport.md`
and `.work/space-device-2026-11-04/CURRENT.md`.
New native sign-ins create a fresh local session ID; token refresh requires the same
account and exact native binding. Preserve that transport fence and the original
SDK identity during group adoption. This increment improves current company chat;
all 54 PRD groups remain open. Live backend 8b40/schema 116 is unchanged.
Release 29 remains a validated artifact pending phone connection; no install or
phone UI action occurred, release 28 is the last confirmed install. Continue long
run, updates, atomic pushes and validated install-only product delivery.

## Organization directory local reads and finite refresh — 4 November 2026

Mobile [243bdcc0](https://github.com/fajarxfce/hris-mobile/commit/243bdcc070fb1fe24782ad671d208769ce36ba4f) is pushed.

Local observation now keeps owned directory facts readable through ordinary credential
expiry while cancelling facts on account/admission changes. One explicit refresh
acquires at most one page of 50; durable terminal cursors stop extra requests,
interrupted refresh resumes, and only admitted publication clears the captured
access revision. Original native lifetime and a 15-second total deadline bound
acquisition, including mutex waits. A separate bounded 2-second path retains a
received denial at cancellation. [Contract](https://github.com/fajarxfce/hris-mobile/blob/243bdcc070fb1fe24782ad671d208769ce36ba4f/docs/collaboration-space-directory-usecases.md),
[evidence](https://github.com/fajarxfce/hris-mobile/blob/243bdcc070fb1fe24782ad671d208769ce36ba4f/docs/measurements/collaboration-space-directory-usecases-2026-11-04.json).

22 focused behavioral cases pass. A slow-consumer denial regression initially
failed and is now fixed by switching guarded access before observing facts.
Root/Android-host/Android dev compile pass 419.463 s /
2,503 fresh cases; browser domain pass 81.676 s /
968 cases, identical 3,917 inputs. Real application DI resolves
the observer and singleton refresh. Format, architecture and detekt pass.
All sessions closed; no new product/provider/FCM/Apple runtime acceptance.

NEXT: reproduce/fix the same nested-flow boundary in the current company-chat list,
then durable group identity/device commands, SDK store adoption and hard-parent
recovery, organization UI and typed group push/background/replies. Draft regression
and transport trace are in `.work/company-chat-observation-2026-11-04/` and
`.work/space-directory-usecases-2026-11-04/`. Product still uses company chat;
all 54 PRD groups remain open. Live backend 8b40/schema 116 is unchanged.
Release 29 remains verified but uninstalled: three 15-second phone connection
attempts timed out. Existing availability question remains pending; release 28 is
the last confirmed installation. Continue long run, updates, atomic pushes and
validated install-only product delivery when the phone returns.

## Product release 29 verified; phone connection pending — 4 November 2026

Mobile evidence [cc87bc29](https://github.com/fajarxfce/hris-mobile/commit/cc87bc297046bcbb7819c055b534a450ebaddf33) is pushed.

Product source [084662c5](https://github.com/fajarxfce/hris-mobile/commit/084662c5bc9277ae8fd56d103a0acda89c3ee5ee) is clean, committed and pushed. The signed devRelease29 APK is
88,107,752 bytes, SHA-256 `e17f1681dcc5cacce43fb126b53da261e5b89423e65c3ab3737d29186c46dca1`.
Package, existing development signer, Firebase dev app, private API, release flags,
16 KiB alignment and backup exclusions are verified; lint has zero issues.
Build/lint plus 18 proof unit tests pass in 548.560 s. The exact ARM64 release APK
opens twice on the dedicated emulator through its native bridge, with 12 stable
seconds per start. Onboarding screenshot is reviewed. The separate proof APK
passes real Android notification/callback/Room/Keystore, three process deaths and
SystemUI reply checks (zero HTTP/Matrix sends). Editor/removal screenshots reviewed.
Owned proof package and emulator are cleaned up. [Evidence](https://github.com/fajarxfce/hris-mobile/blob/cc87bc297046bcbb7819c055b534a450ebaddf33/docs/measurements/android-release-phone-2026-11-04-build29.json).

Phone `10.77.77.3:5555` remains unreachable after two 15-second connection attempts.
No installation or phone UI action was performed; release 28 remains the last
confirmed install. The availability question is pending. Retained APK and guarded
verification/install scripts are in `.work/release-phone-2026-11-04-build29/`.
When the device returns: verify current 28/same signer, notify the user immediately
before `install.py --install-only`, then confirm UID/first install and pull-back SHA.
Never install the proof APK on the phone, clear its data or drive its UI.

NEXT: continue organization-directory local-read/refresh use cases, durable native
identity/device commands, SDK adoption/hard-parent recovery, organization UI and
push/background/replies while phone delivery waits. Product still uses company
chat. All 54 PRD groups remain open; this is neither group rollout nor new provider/
FCM/Apple/voice/meeting acceptance. Live backend 8b40/schema 116 is unchanged.
Continue long run, updates, atomic pushes and validated product installs.

## Atomic company-chat admission cleanup — 4 November 2026

Mobile [084662c5](https://github.com/fajarxfce/hris-mobile/commit/084662c5bc9277ae8fd56d103a0acda89c3ee5ee) is pushed.

A newer access denial can arrive just before an earlier completion clears its
process buffer. Both completion paths now clear only their captured value with a
single atomic compare-and-set. The newer denial remains observable, including
when its durable write is pending. This fixes the existing company-chat cache;
organization directory storage already uses the same mechanism at e97d5e0a.
[Contract](https://github.com/fajarxfce/hris-mobile/blob/084662c5bc9277ae8fd56d103a0acda89c3ee5ee/docs/collaboration-local-list.md),
[evidence](https://github.com/fajarxfce/hris-mobile/blob/084662c5bc9277ae8fd56d103a0acda89c3ee5ee/docs/measurements/collaboration-admission-atomic-2026-11-04.json).

Root :check, Android-host and Android dev compilation pass in 344.93 s
(1,371 fresh cases); browser data suite passes in 92.87 s
(487 cases). Both keep 3,910 identical source inputs. The new shared
regression injects a denial at each final-clear boundary on real SQLite/IndexedDB
with AES-GCM. Format, architecture, detekt and application DI tests pass. All
sessions closed. No new provider, FCM, Apple runtime or product APK acceptance.

NEXT: prepare a validated product update for the authorized phone, then owning
organization-directory local-read/refresh use cases, durable native identity/device
commands, SDK adoption/hard-parent recovery, organization UI and typed group push/
background/replies. No selected HR company may stand in for organization ownership.
All 54 PRD groups remain open. Live backend 8b40/schema 116 and phone release 28
are unchanged. Continue long run, updates, atomic pushes and validated installs.

## Encrypted organization directory storage — 4 November 2026

Mobile [e97d5e0a](https://github.com/fajarxfce/hris-mobile/commit/e97d5e0a9db021034bb6999b468dd97065959f71) is pushed.

Account/space-owned directory facts now survive reauthentication, interrupted paging
and replacement refreshes. Names and company labels remain encrypted; old published
facts stay visible until a replacement completes. Durable terminal cursors and
bounded local reads avoid extra end-page reads. Access denials survive failed or
rejected persistence; exact durable revision plus atomic pending-buffer clear protect
newer denials. Every real host supplies a separate protected content key.
[Contract](https://github.com/fajarxfce/hris-mobile/blob/e97d5e0a9db021034bb6999b468dd97065959f71/docs/collaboration-space-directory-storage.md),
[evidence](https://github.com/fajarxfce/hris-mobile/blob/e97d5e0a9db021034bb6999b468dd97065959f71/docs/measurements/collaboration-space-directory-storage-2026-11-04.json).

Final root :check, Android-host and Android dev compilation pass 346.21 s /
1,370 fresh cases; browser pass 96.303 s / 486 cases. Both keep
3,910 identical source inputs. Seventeen shared cases run on real SQLite/IndexedDB
with AES-GCM; real application DI resolves the new repository. Earlier attempts,
review correction and their validation limits are recorded. All sessions closed.

NEXT: apply the same atomic pending-buffer clear to the existing company-chat list
(the current-then-clear window remains there), then owning local-read/refresh use
cases, durable native identity/device commands, SDK adoption/hard-parent recovery,
organization UI and typed group push/background/replies. This storage adapter alone
does not apply local read admission or perform native acquisition. Product routes
still use company chat. No new product/provider/Apple/FCM or performance acceptance.
Live backend 8b40/schema 116, phone release 28, product/hosted 0b804cb2 and Starter
9959c1d are unchanged.
All 54 PRD groups remain open. Continue long run, updates, atomic pushes and validated
product installs.

## Native group discovery and directory transport — 4 November 2026

Mobile [1da6b5d0](https://github.com/fajarxfce/hris-mobile/commit/1da6b5d00f906e220d6d7b472164d11487541616) is pushed.

A distinct CollaborationSpaceOwner now drives native discovery and bounded contact
pages independently of selected HR company. Initial/current native ownership,
15-second execution limits, conservative monotonic parent deadlines and strict
response/cursor validation reject stale/foreign results. No auto paging, enrollment,
SDK acquisition or local-read reopening. Company labels grant no HR access.
[Contract](https://github.com/fajarxfce/hris-mobile/blob/1da6b5d00f906e220d6d7b472164d11487541616/docs/collaboration-space-discovery.md),
[evidence](https://github.com/fajarxfce/hris-mobile/blob/1da6b5d00f906e220d6d7b472164d11487541616/docs/measurements/collaboration-space-discovery-2026-11-04.json).

Root :check + Android-host pass398.202s/3,899 same inputs/2,463 fresh cases;14 unique
new cases (six data cases also on Android-host). Only a new-test opt-in annotation
changes before the second root pass106.324s/946 fresh domain. Three container-test
assertions then verify the actual new dependency graph:2 focused passes69.803s plus
format/architecture/detekt. Production is identical across all gates. Sessions closed.
MockEngine HTTP is not new backend/provider, Apple or product acceptance.

NEXT: encrypted local group-directory ownership/denial retention, durable native
identity/device commands, SDK store adoption and hard-parent recovery, product UI
and group push/background/reply integration. The current UI still uses company chat;
no group rollout or phone install. Actual original queued-key proof5f9896f7/36c60208
remains accepted separately (strict shields Red, not verified sender trust).
Live8b40/schema116, phone28, product/hosted0b804cb2 and Starter9959c1d unchanged.
All54 PRD groups remain open. Continue long run, updates, atomic pushes and validated
product installs.


## Original queued SDK keys recovered through group completion — 4 November 2026

Backend [5f9896f7](https://github.com/fajarxfce/hris-platform/commit/5f9896f72bb047759ca2378247995eac9b1a533a) is pushed.

Actual Android SDK26.10.23 messages survive native legacy completion, an interrupted
queued-key response and two process deaths. Both unknown/known-peer variants decrypt
the exact original encrypted event with the same original keys and store. The
unmigrated peer and original room need no new-send eligibility expansion. The next
process reads one local event before sync; its subsequent cursor acknowledgment
clears the retained provider queue. Strict SDK shields are Red, not verified trust.
[Protocol](https://github.com/fajarxfce/hris-platform/blob/5f9896f72bb047759ca2378247995eac9b1a533a/docs/collaboration/space-sdk-history.md), [evidence](https://github.com/fajarxfce/hris-platform/blob/5f9896f72bb047759ca2378247995eac9b1a533a/docs/measurements/collaboration-space-sdk-history-2026-11-04.json).

Two actual API/PostgreSQL/Synapse/emulator cases pass in109.162s, with12,388 unchanged
combined inputs and the same APK. Mobile root :check, Android-host, proof unit/build/
lint pass in133.464s (3,883 unchanged inputs;18 fresh proof cases, unaffected tasks
reused). Earlier failed attempts and fixture corrections are recorded. All sessions
and owned emulator resources closed. Mobile proof commit [36c60208](https://github.com/fajarxfce/hris-mobile/commit/36c6020892fe6c7f0a2b2f28772f9a8a31d538a6) is pushed.

NEXT: product group runtime/store adoption, typed group push/background/reply
routing, native hard-parent recovery and actual FCM single-notification handover.
This fixture is not product adoption, worker-role isolation, verified sender trust,
Apple/web or load acceptance. Current Mobile product still ignores group push.
Live8b40/schema116, phone28, product/hosted0b804cb2 and Starter9959c1d unchanged.
All54 PRD groups remain open. Continue long run, updates, atomic pushes and validated
product installs.


## Organization push callback and worker integrated — 4 November 2026

Backend [d0478388](https://github.com/fajarxfce/hris-platform/commit/d0478388ba1a84154cbd42f0f1f4b20b45356178) is pushed.

Mixed private callbacks preserve separate company/group types within one total input
bound and original deadline. The group worker reacquires current ownership before and
after outside-transaction FCM delivery. Exact token disable/readback and captured-revision
ACK commit together; changed tokens defer, and newer callbacks survive old outcomes.
Separate bounded company/group pools share the tested lifecycle implementation.
[Protocol](https://github.com/fajarxfce/hris-platform/blob/d0478388ba1a84154cbd42f0f1f4b20b45356178/docs/collaboration/space-push-delivery.md), [evidence](https://github.com/fajarxfce/hris-platform/blob/d0478388ba1a84154cbd42f0f1f4b20b45356178/docs/measurements/collaboration-space-push-delivery-2026-11-04.json).

Focused:191 pass (272.574 s:147 domain,21 callback,23 data).
Initial API:153/154 pass (233.639 s); the new preservation test
compares PostgreSQL array objects by identity. Only its two SELECT statements change to
compare every column/array value as JSON. Full58 HTTP cases then pass
(139.405 s), alongside the96 configuration/contract cases above.
FULL worker:138 cases,1 existing opt-in report-retention skip
(394.54 s). Format/architecture pass. All four gates retain
8,505 inputs; production sources are unchanged and only that HTTP test changes.
All sessions closed. Actual shared API/worker DI is validated. Cloud transport in new
group delivery tests is injected, not real FCM.

NEXT: typed Mobile group push/background/reply routing and runtime store adoption,
actual SDK original queued-key decryption, native hard-parent recovery and actual FCM
one-visible-notification handover while old/company and group pushers coexist. Current
Mobile product ignores new group envelopes; no group rollout or phone install yet.

Base `f6777bfe` pushed. All 54 PRD groups open. Live8b40/schema116 (push on,
SMTP off), phone28, Mobile product/hosted0b804cb2 and Starter9959c1d unchanged.
Continue long run, regular updates, atomic pushes and validated product installs.


## Organization callback admission accepted — 4 November 2026

Backend [f6777bfe](https://github.com/fajarxfce/hris-platform/commit/f6777bfe67350cc1de7b9761a2f680e6fb02e507) is pushed.

Typed group callbacks reacquire current root account/native/device/gateway/identity,
provider metadata, group eligibility and registration. Short access/MFA/provider expiry
permits only opaque wake. Exact LEGACY completion provenance is separate from the
unchanged interactive admission wrapper. No provider/native token is acquired.
Owned original/published room metadata survives peer offboarding; failed/foreign reads
cannot grant access or retire valid keys. Final current checks roll back late append.
[Protocol](https://github.com/fajarxfce/hris-platform/blob/f6777bfe67350cc1de7b9761a2f680e6fb02e507/docs/collaboration/space-push-admission.md), [evidence](https://github.com/fajarxfce/hris-platform/blob/f6777bfe67350cc1de7b9761a2f680e6fb02e507/docs/measurements/collaboration-space-push-admission-2026-11-04.json).

144 domain cases pass (26.148 s). Initial data compilation rejects
one test-double parameter name under -Werror; only that test name is fixed. All25 data
cases pass (274.722 s):10 new callback cases,1 new completed LEGACY
original-room case,12 native pusher cases and2 retained pusher/gateway regressions.
Format/architecture pass on 8,500 unchanged final inputs. All sessions closed.
No shared DI/HTTP/worker wiring yet. No live or phone installation for this backend step.

NEXT: bounded group worker delivery/token-rotation fencing and mixed company/group
callback adapter, then shared API + FULL worker context gates. Mobile background/reply
owner handover, actual FCM single notification, original SDK queued-key decryption and
native hard-parent recovery remain required. Queue ACK is not a delivered/read receipt.

Base `187a0174` pushed. All54 PRD groups open; rollout disabled. Live8b40/schema116
(push on,SMTP off), phone28, Mobile product/hosted0b804cb2 and Starter9959c1d unchanged.
Continue long run, updates, atomic pushes and validated product installs.


## Organization push wake storage accepted — 4 November 2026

Backend [187a0174](https://github.com/fajarxfce/hris-platform/commit/187a01744775f8e514f8770a66c1a097998bb560) is pushed.

V137 stores separate group wake heads with exact account/device/native provenance,
root/worker isolation, bounded deduplication, finite attempts and global group capacity8.
Captured-revision acknowledgments preserve newer callbacks; expired/replaced leases
cannot consume successors. Strict mapping and transactional readback reject foreign,
malformed or missing rows. TTL uses elapsed seconds, including DST session boundaries.
[Protocol](https://github.com/fajarxfce/hris-platform/blob/187a01744775f8e514f8770a66c1a097998bb560/docs/collaboration/space-push-queue.md), [evidence](https://github.com/fajarxfce/hris-platform/blob/187a01744775f8e514f8770a66c1a097998bb560/docs/measurements/collaboration-space-push-queue-2026-11-04.json).

All 139 domain cases pass initially (58.469 s); data test compilation
then finds ambiguous JUnit imports. Those test imports are corrected. Review also
changes the new SQL TTL constraint to exact elapsed seconds and adds a DST case.
All 28 data cases pass (136.263 s): 15 new PostgreSQL queue cases,
11 original company queue regressions and two native/retained pusher cases. Recovery:
21 pass (0.068 s). Format/architecture checks pass on
8,495 unchanged final inputs. Actual isolated PostgreSQL archive/restore preserves
pending work, lease and encrypted capability. All sessions closed. No shared DI changes.

NEXT: current group callback/background admission, mixed company/group callback dispatch,
worker delivery with token-rotation fencing, and Mobile background/reply ownership.
Queue metadata is not authority or a delivered/read chat receipt. Prove one visible
notification across ownership handover using actual FCM. Actual SDK original queued-key
decryption, group store transition and native hard-parent recovery remain required.

Base `7b81b8e8` pushed. All 54 PRD groups open; rollout disabled. Live8b40/schema116
(push on, SMTP off), normal phone28, Mobile product/hosted0b804cb2 and Starter9959c1d unchanged.
Continue long run, updates, atomic pushes and validated product installs.


## Native organization managed pusher accepted — 4 November 2026

Backend [7b81b8e8](https://github.com/fajarxfce/hris-platform/commit/7b81b8e8562417298872040be60c6f23ab2fc72f) is pushed.

The empty-body managed pusher route now accepts current group gateway ownership.
A separate stable capability is reserved before Matrix I/O; native registration and
current admission are rechecked around both outcomes. Exact publication, audit and
outbox commit together. Rotation, expiry, revocation, failed readback or journal cannot
produce successful acknowledgement. Original Matrix identity survives completed
handover. No native FCM/APNs token is acquired by the pusher operation.
[Protocol](https://github.com/fajarxfce/hris-platform/blob/7b81b8e8562417298872040be60c6f23ab2fc72f/docs/collaboration/space-pusher-access.md), [evidence](https://github.com/fajarxfce/hris-platform/blob/7b81b8e8562417298872040be60c6f23ab2fc72f/docs/measurements/collaboration-space-pusher-access-2026-11-04.json).

Native/domain gate: 167 pass (391.391 s), including 12 new native
PostgreSQL cases, one retained migration case, 18 gateway/key regressions and 136 domain.
Initial API: 152/153 pass (233.24 s); the new preservation test
incorrectly assumed an empty original company namespace for a shared fixture account.
Only that test changes to compare exact original rows before/after registration.
All 57 HTTP class cases then pass (138.375 s), alongside the 96
configuration/contract cases already passing. FULL worker: 138 cases
(382.078 s), 137 passing and one existing opt-in skip.
Format/architecture checks pass on 8,485 unchanged inputs. Production stays
unchanged across all gates; corrected HTTP/FULL worker fingerprints match. All sessions closed.

NEXT: typed group callback admission/queue/worker, mixed original/group callback handling
and Mobile background/reply ownership. Prove one visible notification across ownership
handover using actual FCM; provider registration alone does not prove delivery. Actual
SDK original queued-key decryption, store transition and native hard-parent recovery
remain required. Continue the remaining chat/call/meeting PRD.

Base `172b559c` pushed. All 54 PRD groups open; rollout disabled. Live8b40/schema116
(push on, SMTP off), normal phone28, Mobile product/hosted0b804cb2 and Starter9959c1d unchanged.
Continue long run, updates, atomic pushes and validated product installs.


## Organization Matrix pusher protocol accepted — 4 November 2026

Backend [172b559c](https://github.com/fajarxfce/hris-platform/commit/172b559c1cc3719ef660d8b71e7eac1f2fd9bbfd) is pushed.

The fixed Matrix pusher adapter now accepts separate original/group capabilities with
the same event-only application profile. Both survive retry/restart on the same device
and can be removed independently. Deployable log formatting redacts both namespaces,
including partial keys and exception paths. No native FCM/APNs token enters Matrix.
[Protocol](https://github.com/fajarxfce/hris-platform/blob/172b559c1cc3719ef660d8b71e7eac1f2fd9bbfd/docs/collaboration/space-pusher-protocol.md), [evidence](https://github.com/fajarxfce/hris-platform/blob/172b559c1cc3719ef660d8b71e7eac1f2fd9bbfd/docs/measurements/collaboration-space-pusher-protocol-2026-11-04.json).

Eight transport/actual Synapse cases pass (63.677 s). Fourteen formatter
cases pass (0.101 s), including actual deployment configuration in a
fresh process. Format/architecture checks pass on 8,486 unchanged inputs. All
sessions closed. No schema or DI change; no actual product FCM delivery claim.

NEXT: native group managed pusher admission/reservation/publication, API and FULL worker
DI validation, then callback admission/group queue/worker and Mobile background/reply
ownership. Actual SDK queued-key decryption, group store transition and native hard-parent
recovery remain required. Existing native callers still only generate company capabilities.

Base `892d3497` pushed. All 54 PRD groups open; rollout disabled. Live8b40/schema116
(push on, SMTP off), normal phone28, Mobile product/hosted0b804cb2 and Starter9959c1d unchanged.
Continue long run, updates, atomic pushes and validated product installs.


## Organization push capability storage accepted — 4 November 2026

Backend [892d3497](https://github.com/fajarxfce/hris-platform/commit/892d34973b31a51b15288e891a8e2f1b2d6899dc) is pushed.

V136 retains separate encrypted group push capabilities, bound to exact space/account/
group-device/original-native/Matrix identity. Only immutable reservation to publication
is allowed. Root ownership and same-transaction lookup cleanup are enforced; completed
LEGACY handover is mandatory. Original company push records survive unchanged.
Typed readback and the recovery verifier authenticate the same eight coordinates.
[Protocol](https://github.com/fajarxfce/hris-platform/blob/892d34973b31a51b15288e891a8e2f1b2d6899dc/docs/collaboration/space-push-storage.md), [evidence](https://github.com/fajarxfce/hris-platform/blob/892d34973b31a51b15288e891a8e2f1b2d6899dc/docs/measurements/collaboration-space-push-storage-2026-11-04.json).

150 domain/real PostgreSQL/typed storage cases pass (230.795 s), including
136 domain, six new SQL, two retained migration and six storage cases. Format and
architecture checks pass on 8,484 unchanged inputs. Recovery: 20 pass
(0.011 s), including actual generated SQL/archive/key-rotation
coverage in the storage gate. All sessions closed. No shared DI changed.

NEXT: connect native group pusher reservation/provider publication and both key namespaces
with deployable log redaction, then group callback admission/queue/worker delivery and
Mobile background/reply ownership. Preserve actual SDK original queued-key decryption
proof, store ownership transition and native hard-parent recovery as required work.

Base `7b767bbf` pushed. All 54 PRD groups open; rollout disabled. Live8b40/schema116
(push on, SMTP off), normal phone28, Mobile product/hosted0b804cb2 and Starter9959c1d unchanged.
Continue long run, updates, atomic pushes and validated product installs.


## Native group room metadata and receipts accepted — 4 November 2026

Backend [7b767bbf](https://github.com/fajarxfce/hris-platform/commit/7b767bbfdc6ec59b978d9696ab159084c8727cb3) is pushed.

Group gateway routes now expose managed direct hints, room join/state/membership and
public/private read receipts over exact original/published room ownership. Current
native admission and immutable metadata are rechecked around both provider outcomes.
Receipts require mapped event proof before writing. Historical membership authenticates
group positions; original company positions require fresh group sync. Shared bounded
provider projection preserves original creators and distinct peer aliases.
[Protocol](https://github.com/fajarxfce/hris-platform/blob/7b767bbfdc6ec59b978d9696ab159084c8727cb3/docs/collaboration/space-metadata-access.md), [evidence](https://github.com/fajarxfce/hris-platform/blob/7b767bbfdc6ec59b978d9696ab159084c8727cb3/docs/measurements/collaboration-space-metadata-access-2026-11-04.json).

All 134 domain cases pass initially (27.806 s); data compilation then
failed on one missing test import. That import alone changed. The 72-case data gate passes 70 cases
(558.887 s); two new restart tests retained the old mapped provider
endpoint. Only that fixture changed. Both actual Synapse cases then pass
(50.269 s), including post-restart metadata and receipt projection.
Actual API: 149 pass (237.891 s). FULL worker: 138 cases
(394.062 s), 137 passing plus one existing opt-in skip.
Format/architecture checks pass on 8,473 unchanged final inputs. Production
remained unchanged across all gates; corrected interop/API/worker fingerprints match.
All sessions closed. Actual SDK queued-key decryption remains unproven.

NEXT: group push admission/delivery, actual SDK original queued-key authentication and
decryption, Mobile SDK store ownership transition and native hard-parent recovery.
Preserve original crypto identities and current group authority; do not reuse old
company push ownership for a group device. Continue the remaining communications PRD.

Base `93022a1e` pushed. All 54 PRD groups open; group rollout disabled. Live8b40/schema116
(push on, SMTP off), normal phone28, Mobile product/hosted0b804cb2 and Starter9959c1d unchanged.
Continue long run, updates, atomic pushes and validated product installs.


## Current native group sync accepted — 4 November 2026

Backend [93022a1e](https://github.com/fajarxfce/hris-platform/commit/93022a1e48706e544df4604d60cc873a4dac0576) is pushed.

Group sync now admits the complete original/published room inventory under current
native/device/provider authority. Same-scope positions resume; changed scopes restart;
only exact retained devices may initialize from an authenticated company cursor. Both
provider outcomes and final cursor sealing recheck current authority and the full scope.
Strict Matrix sync HTTP dispatch and shared API/worker DI are connected.
[Protocol](https://github.com/fajarxfce/hris-platform/blob/93022a1e48706e544df4604d60cc873a4dac0576/docs/collaboration/space-sync-access.md), [evidence](https://github.com/fajarxfce/hris-platform/blob/93022a1e48706e544df4604d60cc873a4dac0576/docs/measurements/collaboration-space-sync-access-2026-11-04.json).

Initial native gate: 183 of 184 pass (460.111 s). One combined crypto test
reused a fixed fixture company code in its second scenario; only that test changed to
use separate test databases. Both affected cases pass (56.138 s).
All 133 domain, 34 publication/original migration and eight history cases also pass.
Actual API: 146 pass (228.547 s).
FULL worker: 138 cases (391.017 s), 137 passing plus one existing opt-in
skip. Format/architecture checks pass on 8,465 unchanged final inputs. Only the
native fixture isolation and subsequent API tests changed; all production stayed unchanged.
All sessions closed. Actual SDK queued-key decryption remains unproven.

NEXT: connect room metadata/direct hints/join/read receipts and group push; prove actual
SDK queued original key authentication/decryption, then Mobile SDK ownership transition
and native hard-parent recovery. Ignored room-access protocol drafts are unaccepted.

Base `a0dc8a31` pushed. All 54 PRD groups open; group rollout disabled. Live8b40/schema116
(push on, SMTP off), normal phone28, Mobile product/hosted0b804cb2 and Starter9959c1d unchanged.
Continue long run, updates, atomic pushes and validated product installs.


## Original and group sync protocol accepted — 4 November 2026

Backend [a0dc8a31](https://github.com/fajarxfce/hris-platform/commit/a0dc8a31f8b6173bbbe4964c9b76757ffe1948ed) is pushed.

Group sync now has a bounded provider adapter for the complete original/published room
union and a separate six-coordinate encrypted position. Exact original cursors may
initialize a new scope only for retained devices; fresh devices remain isolated.
Original peer aliases and queued encrypted frames survive room adoption. Both HRIS_
and HRISG_ devices retain finite connection ownership.
[Protocol](https://github.com/fajarxfce/hris-platform/blob/a0dc8a31f8b6173bbbe4964c9b76757ffe1948ed/docs/collaboration/space-sync-protocol.md), [evidence](https://github.com/fajarxfce/hris-platform/blob/a0dc8a31f8b6173bbbe4964c9b76757ffe1948ed/docs/measurements/collaboration-space-sync-protocol-2026-11-04.json).

Initial gate: 153 of 155 pass (94.718 s), including all 133 domain,
actual group/company Synapse sync, cursor and transport cases plus native regressions.
Two new assertions called Jackson's whole-node map instead of mapping array elements;
only that test file changed. All three corrected projection cases pass
(30.791 s), with format/architecture checks on 8,463 unchanged
inputs. Production stayed unchanged. All sessions closed. SDK decryption remains unproven.

NEXT: connect group sync with current native admission, complete inventory checks around
both provider outcomes and after cursor sealing. Validate API and FULL worker contexts.
Then historical peer-key proof, metadata/push, Mobile ownership and hard-parent recovery.
Ignored native sync/HTTP drafts remain unaccepted.

Base `68087090` pushed. All 54 PRD groups open; group rollout disabled. Live8b40/schema116
(push on, SMTP off), normal phone28, Mobile product/hosted0b804cb2 and Starter9959c1d unchanged.
Continue long run, updates, atomic pushes and validated product installs.


## Current native original history access accepted — 4 November 2026

Backend [68087090](https://github.com/fajarxfce/hris-platform/commit/68087090d7f20ced6a552a3a13f133bf9ed76e3e) is pushed.

The existing history routes now read separately proven original rooms when their exact
group binding is absent. Canonical original company/server/account metadata and current
native/device/provider admission remain mandatory. Both provider outcomes recheck the
exact original or published tuple. Peer offboarding does not remove owned history;
new sends still require current recipients and published group room authority.
[Protocol](https://github.com/fajarxfce/hris-platform/blob/68087090d7f20ced6a552a3a13f133bf9ed76e3e/docs/collaboration/space-original-history-access.md), [evidence](https://github.com/fajarxfce/hris-platform/blob/68087090d7f20ced6a552a3a13f133bf9ed76e3e/docs/measurements/collaboration-space-original-history-access-2026-11-04.json).

Initial gate: 173 of 175 pass (353.045 s), including 133 domain, eight
native history and 32 existing publication cases. Two new cases collided on the fixture's
fixed provider room ID; only that fixture was corrected to use unique IDs. All three
affected cases then pass (121.123 s). Actual API: 143 pass
(223.329 s). FULL worker: 138 cases (396.093 s), with
137 passing and the one existing opt-in skip. Quality checks pass on 8,455
unchanged final inputs. Production is unchanged after the initial gate. All sessions closed.

NEXT: complete group sync using the original/published union, distinct sync cursor and
shared bounded provider projection. Prove actual SDK queued-key authentication/decryption
while preserving original store/user/device; embedded sender keys and retained peer data
matter, but source inspection is not acceptance. Then metadata/push, Mobile SDK ownership
transition and native hard-parent recovery. Ignored future sync drafts remain unaccepted.

Base `beccbea1` pushed. All 54 PRD acceptance groups remain open; group rollout disabled.
Live8b40/schema116 (push on, SMTP off), normal phone28, Mobile product/hosted0b804cb2 and
Starter9959c1d unchanged. Continue long run, updates, atomic pushes and product installs.


## Original history protocol accepted — 4 November 2026

Backend [beccbea1](https://github.com/fajarxfce/hris-platform/commit/beccbea1d5f2ecee41ce48d93cafeeb3f962e91f) is pushed.

The group history port now reads explicit original room metadata before a published
group binding exists. Shared bounded projection retains actual original creator and
ciphertext. Original and adopted metadata use the same seven-coordinate group cursor;
only the exact retained device may bridge an old company cursor. Fresh devices remain
isolated. Missing crypto keys, cancellation and interruption retain their classification.
[Protocol](https://github.com/fajarxfce/hris-platform/blob/beccbea1d5f2ecee41ce48d93cafeeb3f962e91f/docs/collaboration/space-original-history-protocol.md), [evidence](https://github.com/fajarxfce/hris-platform/blob/beccbea1d5f2ecee41ce48d93cafeeb3f962e91f/docs/measurements/collaboration-space-original-history-protocol-2026-11-04.json).

Initial gate: 163 of 164 passed in 164.863 s, including all 133 domain,
27 existing data/provider/native and three new protocol cases. One new fixture used
version10/federated defaults instead of the existing version12/nonfederated contract.
The four corrected protocol cases pass in 30.813 s; quality checks
pass on 8,455 unchanged inputs. Only that fixture changed; production and all
other tests remained unchanged. Actual Synapse alternates original/adopted history
after restart and checks fresh original-device reads. All sessions closed. No SDK
Megolm decryption or connected native original fallback is claimed.

NEXT: connect original-room lookup only after successful absent group binding, with
current native admission and exact metadata revalidation around both provider outcomes.
Validate actual API and FULL worker contexts for DI changes. Then complete group sync,
historical peer-key authentication/decryption, metadata/push, Mobile SDK ownership
transition and native hard-parent recovery without replacing original stores/keys.

Base `4c6b9dbd` pushed. Group rollout disabled; all 54 PRD acceptance groups open.
Live8b40/schema116 (push on, SMTP off), normal phone28, Mobile product/hosted0b804cb2
and Starter9959c1d unchanged. Continue long run, updates, atomic pushes and product installs.


## Original and published room read scope accepted — 4 November 2026

Backend [4c6b9dbd](https://github.com/fajarxfce/hris-platform/commit/4c6b9dbd103b41ffdf2b242649aac446c6b2fe2b) is pushed.

A complete pure DIRECT read snapshot now combines separately proven original rooms
and published group bindings. Exact adopted overlaps retain both metadata sources but
appear once; unadopted rooms keep their original identity. Conflicts, duplicate rows,
foreign ownership and incomplete/over-capacity inventories fail without truncation.
Original peers may retain historical aliases while the caller's user stays exact.
Fresh and retained device coordinates are validated even for empty scopes.
[Protocol](https://github.com/fajarxfce/hris-platform/blob/4c6b9dbd103b41ffdf2b242649aac446c6b2fe2b/docs/collaboration/space-read-scope.md), [evidence](https://github.com/fajarxfce/hris-platform/blob/4c6b9dbd103b41ffdf2b242649aac446c6b2fe2b/docs/measurements/collaboration-space-read-scope-2026-11-04.json).

All 160 tests pass in 166.957 s: 133 domain (six new read-scope cases)
and 27 data/provider/native cases. Format and architecture checks pass on 8,454
unchanged inputs. Initial compilation stopped on unnecessary non-null assertions in
the new fixture (15.854 s, zero tests); only that fixture changed.
Production stayed unchanged. Existing actual Synapse and native history regressions
pass, including original/fresh devices and cursor isolation. No new SDK decryption or
queued-key delivery proof is claimed. All sessions closed.

NEXT: original-room history framing/cursors under the same seven group coordinates,
then admitted original history fallback and complete group sync projection/admission.
Retain exact old-device-only cursor bridging and prove historical peer-key lookup plus
SDK decryption before adoption. Follow with metadata/push, Mobile SDK ownership change
and native hard-parent recovery while retaining original keys/store/history.

Base `28a1559c` is pushed. Group rollout stays disabled and all 54 PRD acceptance
groups stay open. Live8b40/schema116 (push on, SMTP off), normal phone28, Mobile
product/hosted0b804cb2 and Starter9959c1d unchanged. Continue long run, updates,
atomic pushes and validated product installs.


## Fresh-device history on original users fixed — 4 November 2026

Backend [28a1559c](https://github.com/fajarxfce/hris-platform/commit/28a1559c3481f40b061b88659001d369feaa1ec3) is pushed.

Fresh HRISG_ devices can now read owned history on an already selected original Matrix
user. The exact canonical room member remains mandatory; device origin no longer
incorrectly forces a new group user. Retained devices still require their original
HRIS_ tuple. Fresh devices cannot use old company/group cursors; their own cursors
remain isolated. No keys, SDK store or provider identity are replaced.
[Protocol](https://github.com/fajarxfce/hris-platform/blob/28a1559c3481f40b061b88659001d369feaa1ec3/docs/collaboration/space-history-device-identity.md), [evidence](https://github.com/fajarxfce/hris-platform/blob/28a1559c3481f40b061b88659001d369feaa1ec3/docs/measurements/collaboration-space-fresh-history-device-2026-11-04.json).

All 127 domain cases passed in the initial gate (23.031 s), which
then stopped on a new test override parameter warning under -Werror. Corrected data/
provider/native selection: all 27 pass (169.466 s), with quality checks
on 8,451 unchanged inputs. Only two test fixtures changed after the initial
run; production is unchanged. Native registration/activation/bootstrap and admitted
history exercise both canonical-user origins. Actual Synapse exercises fresh devices
after restart, original event content and cross-device cursor isolation while old
access remains usable. No fresh-device SDK decryption is claimed. All sessions closed.

NEXT: combine published group and separately proven original owned read metadata.
Allow both fresh devices on original canonical users and completed retained devices;
only the exact retained device may bridge its old cursor. Preserve per-room peer
aliases and queued original key frames, and prove actual SDK peer-key lookup/decryption.
Then group sync cursor/filter/projection, history fallback, metadata/push, full Mobile
adoption and native hard-parent recovery without replacing original stores/keys.

Base `3681619d` is pushed. Group rollout remains disabled and all 54 PRD acceptance
groups remain open. Live8b40/schema116 (push on, SMTP off), normal phone28, Mobile
product/hosted0b804cb2 and Starter9959c1d remain unchanged. Continue the long run,
updates, atomic pushes and validated product installs.


## Original room read inventory accepted — 4 November 2026

Backend [3681619d](https://github.com/fajarxfce/hris-platform/commit/3681619df313d6e27d4d276e1e841bc7e01c0637) is pushed.

Existing V129/V130 metadata now supplies bounded original company/server room inventory
and exact room-ID lookup. Original participants, binding creator and room identity
remain explicit before group adoption. Typed mapping validates every row, finite
limits, deterministic ordering and exact ownership; malformed or partial acquisition
cannot advance a scope. Peer offboarding and selected HR company do not erase original
owned metadata. No schema, DI graph, public provider route or device admission changed.
[Protocol](https://github.com/fajarxfce/hris-platform/blob/3681619df313d6e27d4d276e1e841bc7e01c0637/docs/collaboration/space-original-room-inventory.md), [evidence](https://github.com/fajarxfce/hris-platform/blob/3681619df313d6e27d4d276e1e841bc7e01c0637/docs/measurements/collaboration-space-original-room-inventory-2026-11-04.json).

All 137 tests pass in 139.193 s: 127 domain and ten PostgreSQL/repository
cases. Format/architecture checks pass on 8,451 unchanged inputs. Real V130
fixtures cover multiple rooms/two companies, both original participants, spoofed actors,
wrong namespace, unbound rooms, limits/order, HR selection, peer offboarding and parent
purge. Typed malformed/duplicate/partial readback, invalid requests, acquisition failures,
cancellation and interruption are covered. All sessions closed.

NEXT: merge separately proven original and published group read metadata under current
completed LEGACY owner admission. Exact overlaps must preserve original company, room,
conversation, members and actual creator; never fabricate group lease/publication rows.
A peer may have different original/current aliases in different rooms. Preserve queued
original to-device keys before sync advances and prove actual SDK peer-key lookup and
decryption. Historical read scope does not grant new sends or OTK claims to obsolete
devices. Then group sync cursors/filter/projection, history fallback, metadata/push,
full Mobile SDK adoption and native hard-parent recovery with original stores/keys.

Base `b8e84bba` is pushed. Group rollout remains disabled; all 54 PRD acceptance groups
remain open. Live `8b40` / schema116 (push on, SMTP off), normal phone release28, Mobile
product/hosted `0b804cb2` and Starter `9959c1d` remain unchanged. Continue the long run,
updates, atomic pushes and validated product installs.


## Organization owned-room sync scope accepted — 4 November 2026

Backend [b8e84bba](https://github.com/fajarxfce/hris-platform/commit/b8e84bba57f767e80f45a5b17f8b6dc1a3408126) is pushed.

Existing V131 storage now provides bounded participant-owned room inventory without
joining current peer eligibility. Typed mapping verifies bounds, ordering, ownership
and original metadata. A distinct pure DIRECT scope preserves original Matrix users,
actual creator and LEGACY provenance, rejects duplicate/foreign/future or conflicting
metadata and produces a deterministic digest. A 10,001-row overfetch rejects capacity
above 10,000 without returning a truncated scope. No schema or DI graph changed.
[Protocol](https://github.com/fajarxfce/hris-platform/blob/b8e84bba57f767e80f45a5b17f8b6dc1a3408126/docs/collaboration/space-room-sync-scope.md), [evidence](https://github.com/fajarxfce/hris-platform/blob/b8e84bba57f767e80f45a5b17f8b6dc1a3408126/docs/measurements/collaboration-space-room-sync-scope-2026-11-04.json).

All 130 tests pass in 78.157 s: 127 domain (five new scope cases)
and three expanded PostgreSQL/repository cases. Format/architecture checks pass on
8,451 unchanged inputs. Actual RLS covers root participants, wrong account/
company/space/server, unbound rooms and offboarded peers. Typed acquisition covers
bounds/order/duplicates, cancellation and interruption. Existing exact lookup passes.
All sessions closed. Applied scope drafts are stale; committed source is authoritative.

NEXT: combine separately proven original owned-room inventory for completed LEGACY
owners or establish complete original-room adoption BEFORE provider sync advances.
Otherwise filtering only newly bound group rooms could omit queued original to-device
keys. V129/V130 retain original room/creator metadata; do not fabricate group binding,
lease or publication fields. This scope includes published group bindings only.
Then implement distinct group sync cursor/filter/projection with current admission,
metadata/push, retained-history fallback, full Mobile SDK adoption and native hard-parent
recovery. Preserve original store/passphrase/keys/history and uncertain sends.

Base `96e5053b` is pushed. Group rollout remains disabled and all 54 PRD acceptance
groups remain open. Live `8b40` / schema116 (push on, SMTP off), normal phone release28,
Mobile product/hosted `0b804cb2` and Starter `9959c1d` remain unchanged. Continue the long
run, updates, atomic pushes and validated product installs.


## Current group room-history access accepted — 4 November 2026

Backend [96e5053b](https://github.com/fajarxfce/hris-platform/commit/96e5053b8693410895664c8cf8d1a5b47e8249b5) is pushed.

Messages/context/event routes now admit the current group native/device/gateway and
exact owned room. Completed LEGACY proof precedes original cursor-owner construction.
Reads retain owned history after peer offboarding and require no new own-key query;
new-message sending keeps its separate confirmed receiving-device requirement.
Provider I/O stays outside SQL and current authority/owned metadata are rechecked
around both outcomes. Original finite deadline and exact cleanup remain unchanged.
[Protocol](https://github.com/fajarxfce/hris-platform/blob/96e5053b8693410895664c8cf8d1a5b47e8249b5/docs/collaboration/space-room-history-access.md), [evidence](https://github.com/fajarxfce/hris-platform/blob/96e5053b8693410895664c8cf8d1a5b47e8249b5/docs/measurements/collaboration-space-room-history-access-2026-11-04.json).

Initial domain/runtime gate: 137 passed out of 138 (218.055 s),
including all 122 domain and eight room-message cases with actual Synapse history.
Only a deadline fixture failed. The corrected focused case passes
(42.329 s); both fixture failures and the existing
group error contract are recorded. No production source changed after the initial
gate. Retained-device 32 passes (267.985 s), actual API
143 passes (224.739 s), and full worker
covers 138 cases (393.873 s; 137 passed, one existing optional skip). Quality checks
pass; final retained/worker use 8,448 identical unchanged inputs. API source
differs only in the later retained-device test setup correction: both send and history
assertions now reuse one gateway bootstrap. Its initial 32-test run had one failure
(246.231 s); evidence retains that failure and the correction.
All sessions closed.

Actual Synapse retains original message/context/event reads after restart and peer
offboarding. Completed LEGACY tests carry an old company cursor through group
continuation and a terminal page in the original room, while new SPACE rooms retain
old Matrix identities. HTTP tests cover distinct employees, original encrypted content,
strict bounds/credentials, cursor replay, unrelated rooms and late native revocation.
Opaque fixtures remain transport/storage evidence, not valid SDK Megolm decryption.

NEXT: group sync, metadata and push, then full Mobile SDK adoption and native hard-parent
recovery with original SDK store/passphrase/keys/history/uncertain sends. Group sync
needs its own room-set/filter/cursor semantics; do not blindly reuse a room-history
bridge or substitute a group UUID for a company. Before advancing provider sync,
include separately proven original owned rooms or require complete original-room
adoption: filtering only newly bound group rooms could discard pending original
to-device keys while advancing the cursor. V129/V130 retain original inventory and
actual creator metadata; do not fabricate group bindings for those rows. The current
history routes admit published group bindings only. All applied history-admission drafts
are stale; committed source is authoritative. Shared DI changes require actual API
and full worker gates.

Base `39e85812`, Main `5e1237bf` and Mobile `7f7ef335` are pushed. Group rollout remains
disabled and all 54 PRD acceptance groups remain open. Live `8b40` / schema 116 (push
on, SMTP off), normal phone release 28, Mobile product/hosted `0b804cb2` and Starter
`9959c1d` remain unchanged. Continue the long run, updates, atomic pushes and validated
product feature installs.


## Group room-history protocol accepted — 4 November 2026

Backend [39e85812](https://github.com/fajarxfce/hris-platform/commit/39e8581252b8eecabfd87314bbb7742fe3ec2ca6) is pushed.

Distinct group owner/cursor contracts retain original Matrix user/device/room IDs.
New cursors use a separate group purpose; only an exact LEGACY room/device tuple can
bridge the original company history cursor. Shared company/group projection preserves
bounded pagination, event framing and stable exhaustion. Missing crypto keys and
cancellation retain their classification without fallback or SDK store deletion.
[Protocol](https://github.com/fajarxfce/hris-platform/blob/39e8581252b8eecabfd87314bbb7742fe3ec2ca6/docs/collaboration/space-room-history-protocol.md), [evidence](https://github.com/fajarxfce/hris-platform/blob/39e8581252b8eecabfd87314bbb7742fe3ec2ca6/docs/measurements/collaboration-space-room-history-protocol-2026-11-04.json).

All 139 tests pass in 76.378 s (122 domain, 17 provider/cursor/mapping),
with format/architecture checks on 8,446 unchanged inputs. Actual Synapse
covers SPACE and original LEGACY pagination through restart, no duplicate event IDs,
original content/users/devices/creator, context and event reads. The LEGACY case begins
with an actual old company cursor. Existing company protocol regressions pass.
No DI graph, schema or public group history dispatch changed. All sessions closed.

NEXT: wire fixed group messages/context/event reads under current caller admission
and immutable owned room checks around every provider outcome. The admitted device
must prove completed LEGACY transfer before constructing bridge metadata. Owned
history does not require a currently receiving peer. Keep caller guards and current
provider/gateway/request bounds; shared DI changes require actual API and full worker.
Then finish group sync/metadata/push and full Mobile SDK adoption with original store,
passphrase, keys, history, uncertain sends and native hard-parent recovery. Group sync
cursor/filter migration is separate from this room-history bridge. Applied history
protocol drafts are stale; committed source is authoritative. No valid SDK Megolm
decryption or product rollout is claimed by opaque transport fixtures.

Base `6b9e5d22`, Main `653ae30c` and Mobile `6ebc8677` are pushed. Group rollout remains
disabled and all 54 PRD acceptance groups remain open. Live `8b40` / schema 116 (push
on, SMTP off), normal phone release 28, Mobile product/hosted `0b804cb2` and Starter
`9959c1d` remain unchanged. Continue the long run, updates, atomic pushes and validated
product feature installs.

## Group encrypted room-message send accepted — 4 November 2026

Backend [6b9e5d22](https://github.com/fajarxfce/hris-platform/commit/6b9e5d228b4ad7312d26804aee4fe7239ccb397b) is pushed.
The [fixed group room-message route](https://github.com/fajarxfce/hris-platform/blob/6b9e5d228b4ad7312d26804aee4fe7239ccb397b/docs/collaboration/space-room-messages.md)
uses exact immutable owned rooms, confirmed current recipients and original signed
sender identity. Original Matrix room/device/ciphertext/session/transaction IDs stay
intact. Provider I/O is outside SQL and current admission is checked around either
outcome. Offline short receiving credentials work; offboarding blocks new sends while
owned room metadata remains for separately authorized history.

[Evidence](https://github.com/fajarxfce/hris-platform/blob/6b9e5d228b4ad7312d26804aee4fe7239ccb397b/docs/measurements/collaboration-space-room-messages-2026-11-04.json):
125 policy/repository tests passed (75.939 s), room gateway
8 passed (146.529 s), retained-device 32 passed
(260.625 s), actual API 141 passed
(220.003 s), and full worker 138 passed
(397.325 s; one existing optional skip). All 38 prior
self/key/to-device regressions passed in the initial gateway run. Initial compile,
room and HTTP fixture failures plus their corrections are recorded. Production source
did not change after policy acceptance; only the HTTP fixture differs between final
API/worker and the earlier passed room/retained gates. Quality checks pass.

Actual Synapse returns one room event ID through retry and restart, with one original
ciphertext/session/sender event in peer history. Completed LEGACY devices preserve
original keys and HRIS_ identities in both old and new cross-company rooms. These are
transport/storage proofs, not SDK Megolm decryption or delivered/read receipts. Late
response denial cannot undo remote acceptance.

NEXT: group history/sync/metadata/push and full Mobile SDK adoption with original
store/passphrase/keys/history/uncertain sends and native hard-parent recovery. See the
[backend handoff](https://github.com/fajarxfce/hris-platform/blob/6b9e5d228b4ad7312d26804aee4fe7239ccb397b/docs/agent-handoff.md). History reads must retain owned room scope
independently of peer receiving eligibility. Distinct group owner/cursor types and
an exact admitted LEGACY bridge are required; never clear the SDK store to bypass
recovery. Group rollout remains disabled and all 54 PRD acceptance groups remain open.
Live `8b40` / schema 116 (push on, SMTP off), normal phone release 28, Mobile product/
hosted `0b804cb2` and Starter `9959c1d` remain unchanged. Continue the long run, updates,
atomic pushes and validated product feature installs.

## Group encrypted to-device transport accepted — 4 November 2026

Backend [302a43ac](https://github.com/fajarxfce/hris-platform/commit/302a43ac6392ec652e4fa5f7f9b329ac6c3843eb) is pushed.
The [fixed group route](https://github.com/fajarxfce/hris-platform/blob/302a43ac6392ec652e4fa5f7f9b329ac6c3843eb/docs/collaboration/space-to-device.md) preserves original
transaction IDs, device IDs, ciphertext and message IDs. Every recipient must be
confirmed and current. Detached signed sender/recipient identities must match both
envelope curve keys; native/gateway/request access and pinned observations are checked
around every provider outcome. Short receiving credentials can expire without blocking
an otherwise eligible offline device. Whole-request rejection prevents partial sends.

[Evidence](https://github.com/fajarxfce/hris-platform/blob/302a43ac6392ec652e4fa5f7f9b329ac6c3843eb/docs/measurements/collaboration-space-to-device-2026-11-04.json):
124 policy tests passed (48.001 s), 38 gateway tests passed
(487.809 s), 32 retained-device tests passed
(281.174 s), 139 actual API tests passed
(202.139 s), and the full worker suite passed (138 tests,
387.685 s; one existing optional skip). Quality checks pass.
Final retained/API/worker gates share 8,431 identical unchanged inputs. The initial API
run had 13 HTTP timeouts during observed host memory/I/O pressure; the full retry
passed without changing source or timeouts. Both outcomes are recorded.

Actual Synapse retains an unread original-device frame through LEGACY completion and
restart. In-process retries deduplicate; restart may replay the same to-device frame.
Preserve SDK uncertain sends and duplicate handling. Opaque transport evidence does
not prove valid Olm decryption or delivered/read receipts, and late denial cannot undo
prior remote acceptance.

NEXT: group room content/sync/push and full Mobile SDK adoption with original
store/passphrase/keys/history and native hard-parent recovery. See the
[backend handoff](https://github.com/fajarxfce/hris-platform/blob/302a43ac6392ec652e4fa5f7f9b329ac6c3843eb/docs/agent-handoff.md). Room-content drafts are unapplied; existing
V131 ownership can support bounded exact room lookup without inventing a migration.
Group rollout stays disabled and all 54 PRD acceptance groups remain open. Live
`8b40` / schema 116 (push on, SMTP off), normal phone release 28, Mobile product/hosted
`0b804cb2` and Starter `9959c1d` remain unchanged. Continue the long run, regular updates,
atomic pushes and validated product feature installs.

## Scoped recipient query and claim accepted — 4 November 2026

Backend [dd789d0f](https://github.com/fajarxfce/hris-platform/commit/dd789d0f8b7c0da9b018137edf84f7a32d0a3a15) is pushed.
[Fixed group key query and claim](https://github.com/fajarxfce/hris-platform/blob/dd789d0f8b7c0da9b018137edf84f7a32d0a3a15/docs/collaboration/space-recipient-key-exchange.md)
preserve original Matrix identities and enforce current caller, recipient and request
admission around every provider outcome. Queries include pending publication; claims
verify confirmed signed identities before consuming one-time or fallback keys. Current
native hard authority permits offline receiving despite short access/provider expiry.
Changed observations return HTTP 503 for retry. Company key policy remains intact.

[Evidence](https://github.com/fajarxfce/hris-platform/blob/dd789d0f8b7c0da9b018137edf84f7a32d0a3a15/docs/measurements/collaboration-space-recipient-key-exchange-2026-11-04.json):
121 policy tests passed (44.187 s), 29 gateway tests passed (384.642 s), 32 retained
device tests passed (263.747 s), 137 actual API tests passed (203.967 s), and the full
worker suite passed (138 tests, 386.225 s; one existing optional skip). Quality checks
pass. Final API/worker gates share 8,429 identical unchanged inputs. Two initial HTTP
test setup failures were corrected without production changes and are recorded.
Cross-company PostgreSQL, offline receiving, races, original LEGACY IDs, actual
Synapse key exchange/restart, cancellation and strict HTTP behavior are covered.

NEXT: group to-device transport, room content/sync/push and full Mobile SDK adoption,
including original store/passphrase/keys/history and native hard-parent replacement.
Preserve original transaction IDs; provider restart can replay a to-device envelope,
so SDK uncertain-send and duplicate handling remain required. See the
[backend handoff](https://github.com/fajarxfce/hris-platform/blob/dd789d0f8b7c0da9b018137edf84f7a32d0a3a15/docs/agent-handoff.md). To-device drafts are unapplied.
Group rollout stays disabled and all 54 PRD acceptance groups remain open. Live
`8b40` / schema 116 (push on, SMTP off), normal phone release 28, Mobile product and
hosted checks `0b804cb2`, and Starter `9959c1d` remain unchanged. Continue the long run,
regular updates, atomic pushes and validated product feature installs.

## Scoped offline receiving keys accepted — 4 November 2026

Backend [6fd2819f](https://github.com/fajarxfce/hris-platform/commit/6fd2819ff8a2f9b228e47616a4e2868bd3615f15) is pushed.
[V135 protocol](https://github.com/fajarxfce/hris-platform/blob/6fd2819ff8a2f9b228e47616a4e2868bd3615f15/docs/collaboration/space-key-recipients.md) supplies a minimal
read-only public-key projection for current eligible members and exact verified room
pairs. Short native access/provider expiry permits offline receiving; native hard
expiry, revocation or purge removes eligibility without deleting original provenance.
Completed LEGACY devices retain their exact original Matrix IDs and fingerprint.

[Evidence](https://github.com/fajarxfce/hris-platform/blob/6fd2819ff8a2f9b228e47616a4e2868bd3615f15/docs/measurements/collaboration-space-key-recipients-2026-11-04.json):
full database115 PASS1091.125s; focused13 PASS118.536s and quality checks. Both gates
have 8,423 unchanged inputs; only two recovery tools differ. Typed7 carries on
identical source; recovery19 passes at schema135/unknown136. Ordinary-role scope and
column privileges, exact cross-company room binding, pending keys, actual LEGACY
completion, offline expiry, purge/restore, bounds and cancellation are exercised.
No shared DI change; previous API96/FULLworker138 remain the last graph acceptance.

NEXT fixed group key query/claim, to-device/content/sync/push and full Mobile adoption,
including original SDK store/passphrase/keys/history, uncertain sends and native
hard-parent replacement. Query/claim drafts remain uncompiled/unapplied; see the
[current backend handoff](https://github.com/fajarxfce/hris-platform/blob/6fd2819ff8a2f9b228e47616a4e2868bd3615f15/docs/agent-handoff.md). Actual API/FULLworker validation
is required after DI. This storage checkpoint is not an SDK/product acceptance.
Group disabled; all54 PRD groups open. Live8b40/schema116 pushON/SMTPOFF, normal phone28,
Mobileproduct/hosted0b804cb2 and Starter9959c1d unchanged. Continue long run, updates,
atomic pushes and validated product feature APK installs only.

## Admitted room worker accepted — 4 November 2026

Backend [3284aaf7](https://github.com/fajarxfce/hris-platform/commit/3284aaf7135c3f51df8c8fa72bfcd11bc01e6e35) is pushed.
The [room worker](https://github.com/fajarxfce/hris-platform/blob/3284aaf7135c3f51df8c8fa72bfcd11bc01e6e35/docs/collaboration/space-room-worker.md)
reconciles one immutable SPACE alias or retained LEGACY room with the current
participant's own credentials. Actual creator and publisher remain distinct;
current native/device/provider/peer/request admission surrounds provider outcomes.
Binding, successful head and journal commit atomically. Bounded workers have no
waiting executor queue; deadlines, physical capacity after cancellation and owned
shutdown preserve finite operation. The new room-worker flag defaults to false.

[Evidence](https://github.com/fajarxfce/hris-platform/blob/3284aaf7135c3f51df8c8fa72bfcd11bc01e6e35/docs/measurements/collaboration-space-room-worker-2026-11-04.json):
108 domain plus initial 11 PostgreSQL cases pass in 155.778 seconds; final 43
PostgreSQL/Synapse/retained cases pass in 394.504 seconds. Full worker suite has
137 passed/one existing optional skip in 402.151 seconds; actual API 96 passes in
149.641 seconds. Quality checks pass with 8,413 identical unchanged final API/worker
inputs. The scheduled test's wrong cleanup table name is corrected; its initial
failure and exact source continuity are recorded. All test sessions are closed.
Actual scheduled native/room workers connect two fictional companies to one encrypted
Synapse room; both native views/replays preserve it. Retained-room/current-peer
recovery and original signed device keys survive the exercised provider restart.

NEXT recipient projection/query/claim/to-device, content/sync/push and full Mobile
adoption. Preserve offline receiving, original SDK namespace/passphrase/keys/history,
uncertain sends and native hard-parent recovery. The ignored V135 SQL and five-case
recipient test drafts are uncompiled/unapplied; LEGACY/bounded typed mapping coverage
remains. They are not accepted implementation. Current source and exact continuation:
[backend handoff](https://github.com/fajarxfce/hris-platform/blob/3284aaf7135c3f51df8c8fa72bfcd11bc01e6e35/docs/agent-handoff.md).
No cross-company SDK conversation or product APK is claimed by this worker checkpoint.
Group disabled; all 54 PRD groups open. Live 8b40/schema 116 push ON/SMTP OFF, phone 28,
Mobile product/hosted 0b804cb2 and Starter 9959c1d unchanged. Continue the long run,
updates, atomic pushes and validated product feature installs only.

## Bounded room-control capacity accepted — 4 November 2026

Backend [12020f6b](https://github.com/fajarxfce/hris-platform/commit/12020f6b) is pushed.
V134 ROOM_PREPARATION shares ordinary slots 1–7 with gateway requests, uses current
own native/device coordinates without a gateway, and preserves renewal slot zero.
Expired purpose changes retain the owner and require a new token; exact release and
post-lock deadline checks prevent stale work affecting a successor. [Evidence](https://github.com/fajarxfce/hris-platform/blob/12020f6b/docs/measurements/collaboration-space-room-request-slots-2026-11-04.json):
all 109 database cases pass in 1012.632 seconds; focused six database/eight typed
cases pass in 236.801 seconds, with quality checks and 8,400 identical unchanged
inputs. Recovery 19 passes, maximum schema 134/unknown 135. Upgrade, mixed concurrent
capacity, exact cleanup after purge, malformed readback/cancellation rollback, actual
archive restore and lock-wait expiry pass. Initial unnamed-constraint selection
error is fixed; the original lifetime constraint is preserved. All sessions closed.

NEXT six worker-review drafts (claim/advance/settle/outcome policy, three pure tests,
eleven actual-PG cases) are formatted/parser-checked but UNAPPLIED/UNCOMPILED. Apply
only after this checkpoint. Actual-PG draft uses typed JCE provider rotation and an
explicit worker-capability fixture; do not claim ordinary RLS from it. More real
retained LEGACY/Synapse coverage, scheduler and actual API/FULLworker DI gates remain.
Then recipient keys/content/sync/push, offline keys/native hard-parent replacement
and full Mobile adoption preserving SDK store/passphrase/keys/history. Group disabled;
all 54 PRD groups open. Live8b40/schema116 push ON/SMTP OFF, phone28, Mobileproduct/
hosted0b804cb2 and Starter9959c1d unchanged. Continue long run, updates, atomic pushes
and validated product feature installs only.

## Provider peer recovery accepted — 4 November 2026

Backend [5493a8e1](https://github.com/fajarxfce/hris-platform/commit/5493a8e1) is pushed.
Typed join requires exact room readback; recovered SPACE state retains its actual
canonical creator and requires the current caller to be joined. Only the actual
creator can repair missing initial state. [Evidence](https://github.com/fajarxfce/hris-platform/blob/5493a8e1/docs/measurements/collaboration-space-peer-provider-2026-11-04.json):
105 domain, five repository and four actual Synapse cases pass in 83.55 seconds with
quality checks on 8,398 unchanged inputs. First-creator/second-recoverer lost-response
recovery keeps the same alias, actual creator and both original signed keys after
restart. All sessions closed; no new DI, room worker or SDK-message claim.

NEXT explicit room-preparation request kind sharing V127 slots 1..7. Current GATEWAY
kind requires real gateway coordinates; slot zero is reserved for provider renewal.
Do not fabricate either. Preserve same-owner expired replacement, new token/exact
release, admission/RLS and finite cleanup; then bounded claim/advance/settle worker.
Recipient keys/content/sync/push and full Mobile adoption preserving SDK store and
history remain unfinished. Group disabled, all 54 PRD groups open. Live 8b40/schema
116 push ON/SMTP OFF, phone 28, product/hosted 0b804cb2 and Starter 9959c1d unchanged.
Continue long run, regular updates, atomic pushes and validated product APK installs.

## Peer room recovery and native API accepted — 4 November 2026

Backend [a0653dcf](https://github.com/fajarxfce/hris-platform/commit/a0653dcf) is pushed.
V133 gives each exact participant a private preparation head while allowing only one
active request per conversation. Terminal recovery uses the caller's own proof and
the observed public account plus revision. The immutable binding records the actual
canonical Matrix creator separately from requester and publisher; LEGACY remains
pinned to its original room and creator. Native GET/POST room setup/status is wired.

[Evidence](https://github.com/fajarxfce/hris-platform/blob/a0653dcf/docs/measurements/collaboration-space-room-peer-recovery-2026-11-04.json):
full database 103 and actual API 96 cases pass in 1120.114 seconds; full worker suite
has 127 passed and one existing opt-in skip in 375.998 seconds, with 8,397 identical
unchanged inputs and passing quality checks. Retained publication 31 cases pass in
265.182 seconds. Earlier domain 103 and typed/native 5 each pass with recorded source
continuity. Recovery 19 passes, maximum schema 133 and unknown 134 rejected. Initial
compile/fixture failures are resolved. All test sessions closed.

NEXT provider same-alias recovery using the peer's own credentials, strict actual
creator/state verification and bounded room worker. Provider/pure-policy/join drafts
remain unapplied; HTTP/DI drafts are now applied and stale. Then recipient keys,
content/sync/push, offline keys/native hard-parent replacement and full Mobile adoption
preserving SDK namespace/passphrase, keys/history and uncertain sends. Shared DI
changes require actual API and full worker context checks. Group remains disabled;
all 54 PRD groups remain open. Live 8b40/schema 116 push ON/SMTP OFF, normal phone 28,
Mobile product/hosted 0b804cb2 and Starter 9959c1d unchanged. Continue long run, regular
updates, atomic pushes and validated product feature APK installs only.

## Native room admission checkpoint — 4 November 2026

Backend [95353885](https://github.com/fajarxfce/hris-platform/commit/95353885) is pushed.
Current root native/group/client/device/provider admission now wraps room request and
safe status. Intent, private request, receipt and journal are atomic. Original pair
guards precede account/native/device guards. Actual retained-device transfers and
opposite completed requesters keep original identities and actual room creator.
[Evidence](https://github.com/fajarxfce/hris-platform/blob/95353885/docs/measurements/collaboration-native-space-room-2026-11-04.json):
data41 PASS316.012s with format/architecture on8,393 unchanged inputs; domain103 pass
in an earlier mixed run with all production/domain inputs verified identical. Initial
compile, fixture-clock and replay-assertion failures are resolved and recorded.
All test sessions closed. No HTTP/shared DI/room worker or product caller yet.

NEXT fix peer recovery: V131 first-requester retry strands an uncertain room while
that requester is offline. The other current participant must recover the SAME alias
using its own private proof. Preserve actual canonical SPACE creator or exact LEGACY
creator; never expose/reuse another native session. Per-participant bounded queues
are under review, not accepted. Then actual native HTTP/FULL-worker DI, bounded room
worker, recipient keys/content/sync/push and full Mobile adoption preserving original
SDK namespace/passphrase, keys/history and uncertain sends. Offline keys/hard-parent
replacement remain required. Native source/test drafts are APPLIED/STALE; HTTP/DTO/DI
remain unapplied. Group disabled/all54 groups open. Live8b40/schema116 pushON/SMTPOFF,
normal phone28, product/hosted0b804cb2 and Starter9959c1d unchanged. Continue long run,
updates, atomic pushes and validated PRODUCT feature installs only. StarterPR2 is a
stale Draft targeting the old foundation branch; all commits are already in main.

## Original/group history fence accepted — 4 November 2026

Backend [08827578](https://github.com/fajarxfce/hris-platform/commit/08827578) is pushed.
V132 reserves one immutable unique route for same-original-company canonical pairs,
closing the late-original conversation race. Both inserts arbitrate the same key;
rollback frees it and conflicting preexisting histories abort upgrade. Existing
original bound adoption keeps its LEGACY route. [Evidence](https://github.com/fajarxfce/hris-platform/blob/08827578/docs/measurements/collaboration-original-pair-routing-2026-11-04.json):
focused5 PASS99.972s, full database98/API25 PASS967.86s plus format/architecture on
8,387 unchanged inputs; recovery19 passes (MAX132/unknown133). Both winner orders,
commit/rollback, fixed snapshots, RLS, native purge and actual restore pass. Initial
backfill/DDL ordering failure is resolved. After the gate only one redundant trailing
blank line was removed; both SQL hashes are recorded. No new DI/endpoint/worker claim.

Current backend WIP now applies native room policy, request/status and safe view,
plus shared original pair guard and pure/actual PostgreSQL tests. First gate running;
no native room acceptance yet. Group-room-lifecycle/native-room draft files are now
APPLIED/STALE; use actual source. More retained original-device and opposite initialized
requester coverage, actual HTTP/FULL-worker DI, bounded room worker reconciliation,
recipient keys/content/sync/push and full Mobile adoption remain required. Preserve
SDK namespace/passphrase, keys, history and uncertain sends. Group disabled/all54
PRD groups open. Product/hosted0b804cb2, phone28, live8b40/schema116 pushON/SMTPOFF and
Starter9959c1d unchanged. Continue long run, updates, atomic pushes and validated
PRODUCT feature installs only.

## Organization room storage accepted — 4 November 2026

Backend [4320caca](https://github.com/fajarxfce/hris-platform/commit/4320caca) is pushed.
V131 and strict typed storage preserve immutable canonical/original room intent,
first requester versus actual creator, bounded queue and peer-safe status. Exact
binding and request success commit together; bad readback and cancellation roll back
actual PostgreSQL writes. [Contract](https://github.com/fajarxfce/hris-platform/blob/4320caca/docs/collaboration/space-room-lifecycle.md)
and [evidence](https://github.com/fajarxfce/hris-platform/blob/4320caca/docs/measurements/collaboration-space-room-storage-2026-11-04.json):
all93 database cases PASS797.756s, five typed cases PASS106.634s, format/architecture
and19 recovery cases pass. Shared8,376 inputs match; final typed run adds nine files.
Initial compilation errors are resolved and recorded. All test sessions closed.
No shared DI, room use case/endpoint/worker adapter or Mobile product caller changed.

NEXT shared original/group creation fence. Snapshot-only absence cannot prevent a
later old conversation; immutable old conversations precede every old room producer.
Use an exact unique persistent pair route and test both insert orders, rollback,
repeatable-read concurrency and scoped metadata. Then admitted native room lifecycle,
bounded worker provider reconciliation, recipient keys/content/sync/push and Mobile
preflight/stage/freeze/completion/adoption. Preserve original SDK namespace/passphrase,
keys, history and uncertain sends. Offline receiving and native hard-parent replacement
remain required. Group disabled/all54 groups open. Product/hosted0b804cb2, normal phone28,
live8b40/schema116 pushON/SMTPOFF and Starter9959c1d unchanged. Continue long run, updates,
atomic pushes and validated PRODUCT feature installs only.

## Original room peer verification accepted — 4 November 2026

Group worktree [6415de9c](https://github.com/fajarxfce/hris-platform/commit/6415de9c) adds
bounded original-pair lookup and pure validation of original canonical members/creator
and healthy room state. [Evidence](https://github.com/fajarxfce/hris-platform/blob/6415de9c/docs/measurements/collaboration-space-legacy-room-policy-2026-11-04.json):
99 domain/8 PostgreSQL/3 actual Synapse cases PASS 144.409s with quality checks and
8,374 unchanged inputs. The original peer joins with its own retained user/device;
original room, creator and signed keys survive restart. No original repair, new group
alias, impersonation, logout or key replacement. No SDK-message or product claim.
Commit push succeeded after a transient GitHub failure. V130 remains accepted 6c22caba.

Current uncommitted group worktree: V131 room intent/request queue/binding/status
storage and its migration tests. First gate compiled the migration but failed test
helper nullable Map types before running cases; those were corrected. A second gate
is running with stronger atomic binding/success constraints. No V131 acceptance or
new native/worker command is claimed yet. Queue captures current native/device/provider
proof, retains original requester/creator and gives the peer safe status only.
Continue real PostgreSQL races, expiry, rollback and recovery, then typed storage,
native/worker integration, original-producer fences and full Mobile adoption.

Group disabled/all 54 groups open. Live 8b40/schema 116 push ON/SMTP OFF, normal phone
28, product/hosted 0b804cb2 and Starter 9959c1d unchanged. Preserve original SDK keys,
namespace/passphrase, history and uncertain sends. Offline receiving/hard-parent
replacement remain open. Continue long run, updates, atomic pushes and validated
product feature installs only.

## Original room creator accepted — 4 November 2026

Group worktree [6c22caba](https://github.com/fajarxfce/hris-platform/commit/6c22caba) adds
V130 and exposes the original binding creator as room.createdBy, separately from the
conversation creator. Both participants retain correct attribution through upgrade,
native-parent purge and archive restore. [Evidence](https://github.com/fajarxfce/hris-platform/blob/6c22caba/docs/measurements/collaboration-original-room-creator-2026-11-04.json):
database 4/data 12/API 97 PASS 363.765s with format/architecture and 8,372 unchanged
inputs. One stale recovery future-schema test sentinel was corrected after the gate;
all 19 recovery cases then passed in 0.011s. Encryption projection unchanged from V126.
No DI change or fresh worker claim. The push eventually succeeded after GitHub
commit_refs/500 failures using existing gh HTTPS authentication.

NEXT group-owned room request/publication and original adoption, preserving actual
creator, canonical users, original coordinates and current admission around bounded
provider I/O. A group worktree follow-up currently adds exact original-pair lookup,
pure original-state validation and actual peer join/key-retention/restart proof;
these follow-up changes are uncommitted and not yet accepted. Resolve other legacy
producers/unbound old jobs before rollout; never silently replace old history. Then
recipient keys/content/sync/push and Mobile full adoption preserving SDK namespace,
passphrase, keys and uncertain sends. Offline receiving and hard-parent replacement
remain open. Live 8b40/schema 116 push ON/SMTP OFF, normal phone 28, product/hosted
0b804cb2 and Starter 9959c1d unchanged. Group disabled; all 54 groups open. Continue
long run, frequent updates, atomic pushes and validated product feature installs only.

## Group Matrix room protocol accepted — 4 November 2026

Group branch [0a6ad2b2](https://github.com/fajarxfce/hris-platform/commit/0a6ad2b2) preserves
explicit canonical users from different historical companies with distinct group
alias/immutable marker and strict room-state assessment. [Evidence](https://github.com/fajarxfce/hris-platform/blob/0a6ad2b2/docs/measurements/collaboration-space-room-provider-2026-11-04.json):
96 domain cases pass in initial41.723s; final6 data cases PASS50.407s with quality
checks and8,371 unchanged inputs. Four initial failures were test fixtures only:
JSON child iteration and stale provider endpoint after restart. Production unchanged.
Actual Synapse retains original signed keys, room/alias identity and peer joins;
lost creation responses recover one room and foreign membership is rejected. No SDK
E2EE message or product acceptance. First GitHub push had a transient server error;
the retry succeeded. All provider drafts are applied/stale; use committed source.

NEXT group-owned room request/queue/publication and original adoption. CRITICAL:
V129 inventory lacks binding.account_id, the actual old Matrix room creator. That can
differ from conversation.createdBy. Add public room creator metadata and validate it
against the original pair before adoption, including a fixture whose peer created
the room. Keep requester and Matrix creator distinct; never log in as the peer.
Generic background_jobs remains company-only. Preserve canonical users, original
coordinates, native/device proof and exact lease/current-admission fences. Then
recipient keys/content/sync/push and Mobile adoption. Group disabled/all54 groups open;
Mobile33eb35b2/hosted0b804cb2, phone28/live8b40/schema116 push ON/SMTP OFF and
Starter9959c1d unchanged. Continue long run, updates, atomic pushes and feature installs.

## Native group conversations accepted — 4 November 2026

Group branch [b478f776](https://github.com/fajarxfce/hris-platform/commit/b478f776) adds
native open/list/detail and original-company conversation/room inventory. Current
session/group/peer checks and operation/pair/account/native guards preserve one pair,
atomic receipts/journal and original metadata. [Evidence](https://github.com/fajarxfce/hris-platform/blob/b478f776/docs/measurements/collaboration-space-conversation-native-2026-11-04.json):
data5/API97 PASS222.896s and full worker128 PASS348.067s (127 active/one existing
retention skip), 8,364 identical inputs and quality gates. Opposite company employees,
replay, current revocation/expiry, rollback/cancellation, composite cursors, native
transport and both application contexts pass. No failing gate or schema change.

NEXT explicit group Matrix room provisioning/adoption and canonical participant
verification, then recipient keys/content/sync/push and Mobile adoption. Generic
background_jobs and existing Matrix room request/marker remain company-scoped; never
alias a space into company_id. Four ignored group-rooms/provider drafts are unapplied
and untested. Other applied integration drafts/recorders are stale. Preserve original
SDK namespace/keys/history/uncertain sends; offline keys cannot depend on short native/
provider tokens. Hard-parent replacement remains required recovery. Group disabled,
all54 groups open. Mobilebbba53ea/hosted0b804cb2, phone28/live8b40/schema116 push ON/
SMTP OFF and Starter9959c1d unchanged. Continue long run, updates, atomic pushes and
validated product feature installs only.

## Group conversation metadata accepted — 4 November 2026

Group branch is pushed through [4dd2fa96](https://github.com/fajarxfce/hris-platform/commit/4dd2fa96).
V129 and typed ports retain immutable group pairs, deduplicated current public members
and explicit owned original-company room inventory. [Evidence](https://github.com/fajarxfce/hris-platform/blob/4dd2fa96/docs/measurements/collaboration-space-conversation-storage-2026-11-04.json):
86 database plus6 typed PostgreSQL cases PASS838.663s, 8,360 unchanged inputs,
format/architecture and19 recovery cases. Concurrent openers, rollback/cancellation,
finite overfetch, private-column restrictions and actual bound-room archive restore
pass. Recovery accepts129/rejects130 with V126 encryption unchanged. No Gradle failure;
an initial recovery import error was fixed by running from tool, without code changes.

NEXT native group open/get/list and original inventory with current session/group/peer
checks, atomic receipts/journal and actual API/FULL worker validation. Drafts in the
worktree's ignored group-rooms directory are unapplied; review/format/test before use.
Then explicit room adoption/provisioning, recipient key/content/push authority and
Mobile adoption preserving original SDK keys/history/uncertain sends. Discovery grants
no room authority. Offline keys must not need short native/provider tokens; native
hard-parent replacement remains required recovery. Group disabled, all54 groups open.
Mobile4678042f/hosted0b804cb2, phone28/live8b40/schema116 push ON/SMTP OFF and
Starter9959c1d unchanged. Continue long run, updates, atomic pushes and validated
product feature installs only; never proof APKs or unchanged28.

## Signed group own-key exchange accepted — 4 November 2026

Group branch is pushed through [a65a8170](https://github.com/fajarxfce/hris-platform/commit/a65a8170).
Fixed group upload snapshots bounded signed input, commits immutable reservation before
provider I/O, verifies existing/readback keys and acknowledges atomically under current
native/group/device/provider/request authority. Counts never acknowledge pending keys;
lost or cancelled outcomes retain original identity. Same-identity competitors publish
once, and exact final release preserves successor requests. Upload-only dispatch and
shared DI are wired. [Evidence](https://github.com/fajarxfce/hris-platform/blob/a65a8170/docs/measurements/collaboration-space-key-exchange-2026-11-04.json):
focused18 PASS268.626s; final data20/API132 PASS455.786s; full worker128 PASS343.980s
(127 active/one existing retention skip), 8,351 identical final inputs and quality gates.
Actual PostgreSQL/Synapse restart, retained keys, immutable input, races, revocation,
generation changes and HTTP protocol pass. Two fencing cases and a strict version1
metadata comparison followed initial success; no failing gate.

NEXT explicit group room/member/original-room authority before recipient query/claim/
to-device and content. Include committed version0 with publicationConfirmed in future
recipient keys, verify every returned key, and preserve offline receiving independently
of short native/provider credentials. Directory membership alone grants no room access.
Native hard-parent replacement and full Mobile group adoption remain required, keeping
original SDK namespace/passphrase, history and uncertain sends. Group stays disabled;
no product caller/APK/rollout. All54 groups stay open. Mobile41a6a68c/hosted code0b804cb2,
phone28/live8b40/schema116 push ON/SMTP OFF and Starter9959c1d unchanged. Continue long
run, updates, immediate atomic pushes and validated product feature installs.

## Group public-key identity storage accepted — 4 November 2026

Group branch is pushed through [88f5fc20](https://github.com/fajarxfce/hris-platform/commit/88f5fc20).
V128 and typed ports retain original group/account/device/native/Matrix identity and
one immutable key fingerprint through uncertain uploads and native purge. Only exact
reservation-to-acknowledgement is allowed; current group/native/device scope and
completed LEGACY continuity govern writes. [Evidence](https://github.com/fajarxfce/hris-platform/blob/88f5fc20/docs/measurements/collaboration-space-key-storage-2026-11-04.json):
all 82 database plus 6 data/retained-provider cases PASS 792.520s, 8,350 unchanged
inputs, format/architecture and 18 recovery cases. Initial focused 12 passed database
6/data4 but missed two retained fixture table grants; only that test-role grant changed
before final acceptance. Actual race/expiry/rollback/archive and retained Synapse keys
through restart pass. Recovery accepts 128/rejects 129 without a new encryption purpose.

NEXT fixed group key upload with current request ownership, committed reservation,
signed prior/readback checks and atomic acknowledgement, then recipient pre-ack
availability, explicit room/member authority and Mobile adoption. Concrete unapplied
drafts are in the group's ignored key-storage notes; tracked contract defines the
requirements independently. New DI must validate actual API and full worker contexts.
Offline recipients must not need live short native/provider tokens to expose eligible
keys; native hard-parent replacement remains explicit recovery work. No group endpoint
or product caller is enabled by storage. All 54 groups remain open. Mobile 08b0cecf,
phone28/live8b40/schema116 push ON/SMTP OFF and Starter9959c1d unchanged. Continue long
run, frequent updates, immediate pushes and validated product feature installs only.

## Group gateway self access accepted — 4 November 2026

Group branch is pushed through [941fa2f5](https://github.com/fajarxfce/hris-platform/commit/941fa2f5).
Fixed Matrix identity and capabilities now use exact group gateway ownership, seven
bounded request slots, current native/group/client/MFA and completed retained-device
provenance. Provider and native generations stay pinned across I/O; current denial
withholds either provider outcome. Capabilities requires no provider exchange.
[Evidence](https://github.com/fajarxfce/hris-platform/blob/941fa2f5/docs/measurements/collaboration-space-gateway-access-2026-11-04.json):
focused 7 PASS 114.736s; data 9/API 130 PASS 331.029s; full worker 128 PASS 347.237s
(127 active and one existing retention skip), with 8,340 unchanged inputs and quality
gates. Actual PostgreSQL, Synapse restart, native HTTP renewal/bootstrap/self access,
competing requests, cancellation, replacement and post-provider revocation are covered.

NEXT group immutable public-key reservation/publication, pre-ack recipient key behavior
and room/member/original-room authority, then Mobile adoption preserving original SDK
namespace, keys, local history and uncertain sends. Receiving keys must not depend on
an offline recipient's short native/provider token. Native hard-parent replacement
still requires explicit recovery. Group availability remains disabled; no content
caller, product APK or live rollout. Mobile b84f5ce5/hosted code 0b804cb2, phone 28,
live 8b40/schema 116 push ON/SMTP OFF and Starter 9959c1d unchanged. All 54 groups remain
open. Continue long run, frequent updates, immediate pushes and validated feature installs.

## Native provider recovery and group maintenance accepted — 4 November 2026

Group branch is pushed through [b2dee963](https://github.com/fajarxfce/hris-platform/commit/b2dee963).
Explicit native renewal uses slot0, current admission and exact retained provenance,
outside-SQL login/key readback and atomic provider-generation/receipt/journal writes.
Original Matrix identity/creation time/keys remain intact. Native HTTP exposes a safe
versioned receipt. Group replay/request cleanup shares the existing owned worker timer
with bounded atomic batches and cancellation on stop.
[Evidence](https://github.com/fajarxfce/hris-platform/blob/b2dee963/docs/measurements/collaboration-space-provider-renewal-2026-11-04.json):
focused8 PASS150.027s, actual retained provider/restart35.204s; final coverage domain93,
data41, API94 and worker128 (127 active/one existing retention skip). Final data6/API94
PASS226.232s and full worker PASS352.928s, identical8,338 inputs and quality gates.
All preliminary test-only corrections, exact hashes and reproduction commands are recorded.

NEXT current group gateway exchange, key/room/member authority and original-room
inventory, then Mobile preflight/handover/atomic adoption across history/outbox/read/
push/deep links. Preserve original SDK namespaces and uncertain sends. Availability
remains disabled; no Mobile group caller, rollout or new product APK. All54 groups
remain open. Phone28/live8b40/schema116 pushON/SMTPOFF, Mobile7fdb55e6/hosted0b804cb2
and Starter9959c1d unchanged. Continue full long run, updates, atomic immediate pushes
and validated product feature installs. Implementation stays in the group worktree.

Read-only Starter PR audit: PR2 (enterprise foundations) is a stale Draft targeting
feat/platform-foundations. Freshly fetched origin/main dc14934c contains its entire
head594c3fb0 (main...head63/0, merge-base=head); all six PR checks pass. No missing
PR commit, conflict or CI failure explains its open status. No close/retarget/merge
was requested or performed. The user's question was answered while work continued.

## Group request ownership accepted — 4 November 2026

Group branch is pushed through [88d20851](https://github.com/fajarxfce/hris-platform/commit/88d20851).
V127 and typed storage bound each group device to seven ordinary gateway requests
and one reserved provider-renewal request. Exact release preserves a successor;
expired cleanup retains device/key provenance after offboarding or native purge.
[Evidence](https://github.com/fajarxfce/hris-platform/blob/88d20851/docs/measurements/collaboration-space-request-storage-2026-11-04.json):
six focused PostgreSQL cases PASS92.446s, then all76 database plus five typed-data
cases PASS679.944s on8,333 unchanged inputs, format/architecture and17 recovery tests.
The initial test-only JDBC nullability compile correction is recorded.

Next wire explicit native provider renewal, current gateway request authority and
bounded request/replay maintenance, then rooms and Mobile handover/adoption. The
storage increment grants no access and adds no DI, provider exchange or rollout.
Mobile766572b6/accepted code0b804cb2, phone release28/live8b40/schema116 pushON/SMTPOFF
and Starter9959c1d stay unchanged. All54 groups remain open. Continue the full long
run, frequent updates, atomic immediate pushes and validated product feature installs.
Implementation remains in the group worktree; this peer checkpoint is documentation.

## Group gateway native transport accepted — 4 November 2026

Group branch is pushed through [272b66b5](https://github.com/fajarxfce/hris-platform/commit/272b66b5).
Native bootstrap returns original Matrix identity atomically with group credentials;
status supplies safe gateway revision/deadline recovery metadata. Distinct group
refresh dispatch, native observed-version/idempotency HTTP and module DI are wired.
[Evidence](https://github.com/fajarxfce/hris-platform/blob/272b66b5/docs/measurements/collaboration-space-gateway-http-2026-11-04.json):
93 domain/33 data pass, API/OpenAPI92 PASS158.550s and full worker127 PASS356.150s
(126 active/one existing retention skip),8,324 final inputs unchanged, format and
architecture PASS. Three preliminary failures and test-only corrections are recorded.
Actual elapsed recovery42.128s and offboarding11.098s pass; old refresh remains covered.

NEXT bounded group request ownership, native provider renewal after expiry and group
replay maintenance, then group room authority and Mobile key preflight/handover/full
history/outbox/read/push/deep-link adoption. Expired gateway replay's specific401 is
not evidence that the native parent or owned local history should be discarded.
Do not reopen original company runtime slots or change the original SDK namespace.
Group feature remains disabled; no provider/room exchange, Mobile caller or rollout.
Mobile68b9232b/hosted code0b804cb2, normal phone28/live8b40/schema116 pushON/SMTPOFF
and Starter9959c1d unchanged. All54 groups remain open. Continue full long run, frequent
updates, atomic pushed commits and validated product feature builds on the phone.
Implementation continues in the group worktree; this peer change is documentation only.

## Native group gateway issuance accepted — 4 November 2026

Group branch is pushed through `6b92246a`. Native bootstrap/refresh enforce current
root-account/native/group/client/MFA, canonical identity, exact device/provider and
completed retained-transfer provenance. Credential/receipt/journal writes are atomic;
replay and final response remain subject to fresh authority. No provider token is
decrypted or exchanged. [Evidence](https://github.com/fajarxfce/hris-platform/blob/6b92246a/docs/measurements/collaboration-space-gateway-issuance-2026-11-04.json):
14 focused cases PASS167.690s; broader 154 PASS343.118s (92 domain, 62 data including
all 28 worker-publication cases), 8,321 unchanged final inputs, format/architecture.
The final retained refresh test passed its actual bounded cooldown and kept the old
runtime DRAINING. A test-only -Werror correction is recorded; no failing assertion.

NEXT native HTTP needs an atomic bootstrap envelope with original Matrix user/device
IDs captured with its token pair, plus safe gateway revision metadata in group status
for recovery after replay expires. Never derive the retained Matrix device ID from
its new group UUID. Then native bootstrap/Matrix refresh dispatch, strict OpenAPI and
new DI, with actual API plus FULL worker contexts. No group HTTP/provider/room exchange
or Mobile adoption is enabled yet. Continue runtime/history/outbox/read/push/deep-link
ownership and all 54 open communications groups. Mobile a2979db2 remains locally/hosted
accepted, phone release 28/live 8b40/schema 116 and Starter 9959c1d unchanged. Continue long run,
updates, atomic Git handoffs and immediate pushes; implementation stays in group worktree.

## Organization gateway storage and hosted Mobile verification — 4 November 2026

Group branch `feat/communication-space` is pushed as `338915f5`. V126 and typed encrypted
gateway ports preserve separate group/account/native/device ownership, verify exact
post-write readback, retain immutable provenance and support bounded response recovery.
[Evidence](https://github.com/fajarxfce/hris-platform/blob/338915f5/docs/measurements/collaboration-space-gateway-storage-2026-11-04.json):
19 focused cases PASS154.081s; 181 broader cases PASS888.458s (70 database, 26 domain,
85 data), identical 8,314 inputs, format/architecture and 16 recovery-tool cases. Actual
PostgreSQL covers scope, concurrent refresh, cancellation rollback, key rotation,
archive restore and SQL/Kotlin authenticated-context parity. Initial fixture correction
is recorded; migration SQL stayed unchanged. No group gateway HTTP/DI or deployment.

Mobile `21eca8e6` records all hosted acceptance for code `0b804cb2`:
[verified evidence](https://github.com/fajarxfce/hris-mobile/blob/21eca8e6/docs/measurements/collaboration-runtime-handover-hosted-2026-11-04.json).
Control has 3,118 portable and 1,219 Apple-simulator tests and iOS host compilation;
native-HRIS has 5 actual server/provider/emulator scenarios, 241 Android-host and 18
proof-host cases. Both Android/web protocol modes pass. Archive digests, exact source
hashes and SystemUI reply images are checked. Actual Apple Matrix/APNs/CallKit remain
unfinished; new local handover stage/resume is separately accepted in 67.337s.

NEXT current group gateway/bootstrap/refresh and completed-transfer admission, room
authority, Mobile provider-key preflight and full group runtime/history/outbox/read/
push/deep-link ownership. Preserve original SDK namespaces and require successful local
stage/current admission before permanent freeze. All 54 communications groups remain
open. Normal phone release 28/live 8b40/schema 116 and Starter 9959c1d unchanged. Continue full long
run with updates, atomic handoffs and immediate pushes. This main checkout has no
new product code or deployment; continue implementation in the group worktree.

## Durable Mobile handover accepted locally; group gateway continues — 4 November 2026

Mobile code is pushed as `0b804cb2`. Local handover reads/stages immutable protected
schema2 intent after exact observed-client pinning, acquisition exclusion and full
old SDK drain. It preserves the original namespace/passphrase and blocks legacy
restore/install across restart. It grants no server authority and has no product caller.

[Evidence](https://github.com/fajarxfce/hris-mobile/blob/0b804cb2/docs/measurements/collaboration-runtime-handover-2026-11-04.json):
root/Android/proof PASS160.404s (4044 desktop,241 Android-host,18 proof,10 build-logic;
18 fresh finally,1958 fresh in preceding run), browser463 PASS98.262s, same3880 inputs.
Actual SDK/Keystore fault/cancel/stage/process-restart/key-continuity PASS67.337s.
One ignored-draft format failure and one older proof-adapter compilation fix are recorded;
no failing test assertion. Owned proof app cleared and emulator3731489/5562 stopped.
No user phone interaction. Debug proof APK must never be installed on the phone.

Hosted runs on exact0b804cb2 are still in progress: control41775492328,
native-HRIS41775492365, protocol41775492415. Collect outcomes/artifacts/source hashes;
local acceptance does not replace those hosted checks or actual Apple runtime work.
Group backend remains c9bf7762 (eight native metadata/setup operations,127 full worker
and137 domain/API/OpenAPI checks); main805c181f records its acceptance.

NEXT group gateway/room authority and current admission, then Mobile provider-key
publication/readback preflight and group store/inbox/outbox/history/read/push/deep-link
ownership. No permanent server freeze before successful local stage/current admission;
never replace an old company ID with a space ID in its SDK namespace. Cross-company
end-to-end acceptance and all54 communications groups remain open. Normal phone28,
live8b40/schema116 pushON/SMTP OFF and Starter9959c1d unchanged. Continue full long run,
frequent updates, atomic commits and immediate pushes.

## Native group HTTP accepted; durable Mobile handover underway — 4 November 2026

Group branch is pushed through `c9bf7762`. Eight native-only routes expose current
organization chat space, bounded cross-company contacts, identity selection, device
registration/status/retry and retained-runtime freeze/drain. Fresh native admission
is distinct from HR company admission; defaults remain disabled. HTTP/OpenAPI tests
cover current credentials, revocation, cross-company labels and retained replay.

[Evidence](https://github.com/fajarxfce/hris-platform/blob/c9bf7762/docs/measurements/collaboration-space-native-http-2026-11-04.json):
137 domain/API/OpenAPI checks PASS188.105s; full worker/context127 PASS377.895s
(126 active/one existing retention skip), same8299 inputs and quality checks. Two
initial failures, shared primitive schema fix and fixture corrections are recorded.
No deployment or product caller is enabled. Normal phone28/live8b40/schema116 unchanged.

Mobile WIP adds durable exact-target SDK intent read/stage, exclusive acquisition gates,
full client drain before protected schema2 write, legacy restore/install rejection,
and record/registry/metadata cancellation tests. Focused validation is running; actual
Android encrypted-store proof and required full gates remain. Do not ship WIP. Then
provider publication preflight, group room/gateway and full inbox/outbox/read/push/
deep-link ownership, with cross-company end-to-end acceptance. All54 groups remain open.
Continue the full authorized long run, frequent updates, atomic commits and pushes.


## Completed native recovery accepted; group HTTP continues — 4 November 2026

Group branch is pushed through `631a2d3d`. Current native registration/retry/freeze/
drain now validate exact immutable completion for an ACTIVE retained target. Original
identity/key/retirement and successful queue provenance remain pinned; current native/
group/client/MFA admission is mandatory. Ordinary native refresh can recover receipts
without a new device, queue request or freeze. Fresh retry of successful work stays
denied and drain observes actual retained server slots.

[Evidence](https://github.com/fajarxfce/hris-platform/blob/631a2d3d/docs/measurements/collaboration-completed-native-replay-2026-11-04.json):
27 actual PostgreSQL cases PASS124.865s, API47 PASS168.496s, full worker127
PASS370.67s (126 active/one existing opt-in retention skip), same8291 unchanged inputs
and quality checks. Initial expired-enqueue fixture failure and its test-only fix
are recorded. No group HTTP/Mobile caller or deployment is enabled by this commit.

Next group HTTP discovery/contacts/selection/device/status/retry/freeze/drain and
actual HTTP/OpenAPI/API/worker validation, then rooms/gateway and durable Mobile
handover. Mobile record/persistence review has started without source changes:
closeOtherOwners retires only present entries, so a durable exact-target gate must
also prevent future foreground/background/process-restart SDK acquisition and drain
pending reservations. Confirm old key publication/readback before permanent freeze;
keep the original encrypted store namespace/passphrase and uncertain sends.

Normal phone28, live8b40/schema116 pushON/SMTP OFF, Mobile3d85d569/hosted gates and
Starter9959c1d remain current. All54 groups and actual Apple runtime remain open.
Continue full long run, frequent updates, atomic Git handoffs and immediate pushes.

## Retained worker activation accepted; completed native replay continues — 4 November 2026

Group branch is pushed through `5b66e661`. Retained activation now observes current
native/group/client/MFA proof, exact source/key/gateway metadata, frozen target and
zero live old slots; it reads signed provider keys outside SQL and atomically retires
the original device/provider/gateway with group publication and journal. Busy work
uses bounded existing retry/deadline budgets. The old SPACE path remains covered.

[Evidence](https://github.com/fajarxfce/hris-platform/blob/5b66e661/docs/measurements/collaboration-retained-worker-activation-2026-11-04.json):
focused23 PASS133.163s, API47 PASS156.651s, full worker127 PASS365.38s (126 active and
one existing opt-in retention skip), same8290 unchanged inputs and quality checks.
Actual PostgreSQL verifies competing workers, every write-boundary rollback and
current/late revocation; actual Synapse readback/restart retains user/device/keys.
Three failed initial runs and their fixture-only fixes are recorded. Owned resources
are closed. No new phone/live deployment or product HTTP/Mobile caller is enabled.

Current group WIP implements explicit completed native registration/retry/freeze/drain
replay with a pure continuity policy and actual regression cases; follow its current
handoff/gates. Then durable Mobile key-publication preflight, future SDK-acquisition
gate and exact original store/inbox/outbox/read/push ownership, plus group HTTP/rooms/
gateway and cross-company end-to-end acceptance. Public fingerprint alone is not
private-key proof or confirmation of provider publication.

Normal phone28, live8b40/schema116 pushON/SMTP OFF, Mobile3d85d569/hosted gates and
Starter9959c1d remain current. All54 groups and actual Apple runtime remain open.
Continue full long run, frequent updates, atomic Git handoffs and immediate pushes.

## Owned gateway metadata accepted; retained activation underway — 4 November 2026

Group branch is pushed through `612d8279`. V125 exposes exact owned original gateway
metadata through a restricted non-login view; no hashes/replay/token columns. The
typed port distinguishes absent, wrong-parent and retired heads. Current admission
still governs use; the metadata increment has no product caller.

[Evidence](https://github.com/fajarxfce/hris-platform/blob/612d8279/docs/measurements/collaboration-legacy-gateway-metadata-2026-11-04.json):
18 focused cases PASS186.803s;151 broad database/domain/storage/native cases
PASS728.592s, same8290 unchanged inputs, format/architecture PASS. Actual role
isolation, retirement/parent purge/archive restore, strict mapping and failure/
cancellation/interruption pass. Recovery125/future126 rejection passes15 tests.
No failed run; all owned resources closed.

Current group WIP applies admitted retained-worker activation, provider key readback,
atomic old/new publication, bounded busy retry, DI and actual database/Synapse tests.
It is unaccepted; follow the group worktree's handoff and gates. Next explicit
completed native replay and durable Mobile SDK/store/inbox/outbox/read/push ownership.
Normal phone28, live8b40/schema116 pushON/SMTP OFF, Mobile3d85d569/hosted gates and
Starter9959c1d remain current. All54 groups and actual Apple runtime remain open.
Continue full long run, frequent user updates, atomic Git evidence and immediate pushes.

## Typed completion port accepted; gateway observation continues — 4 November 2026

Group branch is pushed through `60b58d3d`, on accepted V124 `a22816d0`. The typed
completion repository validates exact owned mapping and successor versions after
the atomic retirement function. Missing/foreign/unreadable readback rolls the caller
transaction back; interruption skips another SQL read and preserves its flag.

[Evidence](https://github.com/fajarxfce/hris-platform/blob/60b58d3d/docs/measurements/collaboration-legacy-runtime-completion-ports-2026-11-04.json):
12 mapping/actual storage cases PASS111.923s with8280 unchanged inputs and passing
format/architecture. Actual wrong-owner/stale-version, nullable gateway, readback
failure rollback and API capability denial pass. Earlier compilation errors and
Java jOOQ/nullable-version fixes are recorded. No DI/worker/client caller yet.

Current group WIP is V125 narrow owned original-gateway metadata plus typed read
port, needed to observe its exact version before completion without exposing hashes
or encrypted replay. Follow that worktree's handoff for active checks. Then continue
admitted retained worker using existing provider key readback, completed replay and
durable Mobile runtime/store/inbox/outbox/read/push ownership. Live8b40/schema116,
normal phone28, Mobile code3d85d569/hosted gates and Starter9959c1d remain current.
All54 groups and actual Apple runtime remain open. Continue full long run, frequent
user updates, Git evidence and immediate pushes.

## V124 atomic retained completion accepted — 4 November 2026

Group branch `feat/communication-space` is pushed at `a22816d0`. V124 retains an
immutable completion mapping and a restricted worker-only function that retires the
original device/provider/optional gateway in the same transaction as group
credential/ACTIVE device/SUCCEEDED queue publication. Current proof, exact versions
and zero live original slots are checked with DB time after blocking guards.
PUBLIC/API execution is denied; the non-login owner cannot read credential secrets.

[Evidence](https://github.com/fajarxfce/hris-platform/blob/a22816d0/docs/measurements/collaboration-legacy-runtime-completion-2026-11-04.json):
six actual migration cases PASS128.582s/8273 unchanged inputs;133 broad database,
domain/storage/native cases PASS603.054s/8275 inputs;15 recovery-tool cases pass.
Format/architecture pass. Tests cover caller rollback/cancellation/interruption,
actual lease and blocked native expiry, immutable owned mapping after parent purge
and archive restore. No failed run. Recovery now supports124.

Completion repository work is now uncommitted in the group worktree; follow its
current handoff and source checks. V124 has no application/worker/HTTP/client caller
yet. Synthetic database key/credential metadata is not provider or SDK acceptance.
Next typed port, admitted retained worker with existing exact key readback and
completed replay, then durable Mobile gate/store/inbox/outbox/read/push handover.
Mobile code3d85d569/hosted gates, normal phone28, live8b40/schema116 pushON/SMTP OFF
and Starter9959c1d remain current. All54 groups and actual Apple runtime remain open.
Continue the long run, user updates, Git evidence and immediate commit pushes.

## Native freeze and drain accepted — 4 November 2026

Group branch `feat/communication-space` is pushed through `9d3c8a99`, following
`57b2bee3` and `53a86f48`. Current native freeze atomically records one exact retained
target with receipt/journal and preserves replay through refresh. Drain reads at
most eight original server slots under current admission, reports active count and
latest expiry, and never mutates runtime/queue state. No DI/HTTP is exposed yet.

[Freeze](https://github.com/fajarxfce/hris-platform/blob/57b2bee3/docs/measurements/collaboration-legacy-runtime-freeze-2026-11-04.json)
passes18 cases/64.356s/8268 unchanged inputs; [drain](https://github.com/fajarxfce/hris-platform/blob/9d3c8a99/docs/measurements/collaboration-legacy-runtime-drain-2026-11-04.json)
passes23/69.9s/8271, both with format/architecture. Actual database cases cover root
initialization, competing calls, every write-stage rollback, current/late denial,
cancellation/interruption, real lease expiry and concurrent cleanup. All owned
resources are closed. No failure in either first run.

Next atomic group publication/old device-provider-gateway retirement and immutable
completed mapping, then durable Mobile SDK/store/inbox/outbox/read/push handover.
Zero server slots is not SDK shutdown or remote rollback. Existing Matrix key
repository already supports exact device readback and pure signature/fingerprint
validation; reuse it. Group handoff/transfer contract contain the current review.
Mobile code3d85d569 and hosted gates, normal phone28, live8b40/schema116 pushON/
SMTP OFF and Starter9959c1d remain current. All54 communications groups and actual
Apple runtime remain open. Continue the full long run, frequent updates, immediate
commit pushes and installation of validated product builds.

## All legacy runtime consumers fenced — 4 November 2026

Group implementation in `/home/fajar/hris/.worktrees/communication-space` is now
accepted and pushed at `53a86f48`. Bootstrap/refresh retain original runtime guards;
provisioning/direct-room workers hold bounded slots around provider I/O and reject
late results after a freeze. Together with `0bf751e5`, all fixed legacy consumers
are fenced. Current admission, cancellation/interruption and original keys remain
protected. Gateway refresh requires no provider slot.

[Evidence](https://github.com/fajarxfce/hris-platform/blob/53a86f48/docs/measurements/collaboration-legacy-runtime-consumers-2026-11-04.json):
19 focused, 166 full native data, 47 API, 126 active worker plus one existing opt-in
skip, and five actual Android SDK cases pass. All five runs use identical 8264-input
snapshots and pass format/architecture. Android took 1035.238s; the three recovery
states each retain 176 originals, with SDK receipt and automatic WorkManager
recovery. All owned fixtures and the exact emulator are stopped. Earlier failures
and corrected fixture clocks remain recorded; production deadlines are unchanged.

Next: admitted native freeze/replay and bounded drain status, then atomic retained
group publication/old credential retirement and completed-mapping replay continuity.
Formatted uncompiled drafts are in the group worktree's ignored runtime-freeze-next;
actual database tests remain. Existing V121 already reserves terminal device revision
1000000 for revocation; no new boundary migration is needed. Mobile needs durable
SDK gating, original namespace/passphrase retention and coherent room/read/push
handover. No public group transfer or new product build is enabled by this increment.

Mobile implementation `3d85d569` and all three hosted gates remain accepted. Normal
phone release 28, live `8b40`/schema 116 with push ON and SMTP OFF, and Starter
`9959c1d` stay current. Actual Apple Matrix/APNs/CallKit and all 54 communications
groups remain open. Continue the authorized long run, frequent user updates, Git
handoffs, immediate push of each commit and installation of validated product builds.

## Legacy gateway fenced; remaining consumers continue — 4 November 2026

Implementation continues in /home/fajar/hris/.worktrees/communication-space on
feat/communication-space, now0bf751e5 pushed. Its latest handoff and
[measurement](https://github.com/fajarxfce/hris-platform/blob/0bf751e5/docs/measurements/collaboration-legacy-runtime-gateway-2026-11-04.json)
are authoritative. V117–123 directory/canonical identity/device/credential/queue and
runtime storage foundations remain accepted. AccessNativeCollaborationMatrixDevice
now captures one <=60-second original-runtime lease for each fixed gateway request,
including provider renewal. Current admission plus runtime/exact slot guards apply
before/after I/O and at publication. Finally cleanup preserves cancellation and
interruption, capacity is bounded at eight, and old keys/winning tokens are retained.
No losing Matrix candidate is logged out; expired leases do not prove remote rollback.

Accepted: full native data160 PASS243.562s; API47 PASS152.165s; full worker127 cases
(126 active/one existing opt-in retention skip) PASS342.882s; actual Android SDK5
PASS1047.184s. All8264 checked inputs stay unchanged within each run, with format
and architecture passing. The three worker states each recover176 originals;
SDK receipt and WorkManager process-death/network restoration pass. All owned
emulators/tests are stopped. Earlier failed runs are retained, including setup
refusal to overwrite an owned proof package and later verifier-token expiry. The
verifier now requests fresh same-identity credentials through normal HTTP after SDK
cleanup; production token lifetimes remain unchanged. Later fixture-only source
differences from data/API gates are explicit in the measurement.

Next apply/review six prepared files for bootstrap/refresh and legacy provisioning/
direct-room worker fences, including shared DI, actual repository factories and six
new test methods. The focused selection has18 cases, then full native/API/worker/
current SDK checks. These drafts are not yet accepted. Group worktree handoff and
ignored runtime-consumer-next notes retain lock ordering, base hashes and exact
next commands. No public transfer or group runtime is enabled by the gateway unit.
Follow with admitted atomic group publication/legacy retirement and durable Mobile
SDK drain/alias/inbox/outbox/read/push ownership. Never acquire old job/room guards
after account/native guards or change the old company/native SDK directory hash.

Mobile377d37b4 records this state; implementation remains3d85d569 with all hosted
gates accepted: protocol41741297195 native/classic, control41741297181 (3114 portable,
1217 Apple plus full Xcode), native41741297183 (232 Android-host,18 proof-host,5 native
and three inspected SystemUI PNGs). Actual Apple Matrix/APNs/CallKit and all54
communications acceptance groups remain open. Normal phone28, live8b40/schema116
pushON/SMTP OFF and Starter9959c1d remain current. No product installation or live
migration occurred in this server increment. Continue the full authorized long run,
frequent user updates, Git evidence and immediate push of every atomic commit.

Older source/running-check notes below are historical.

## Group graph verified; release 28 remains installed — 3 November 2026

Continue implementation in /home/fajar/hris/.worktrees/communication-space on
feat/communication-space, latest pushed e905b8d6 (preceded byfc1a6c40,84e37752,6dde1489,
d92d4206). Its docs/agent-handoff.md and collaboration/group-provisioning.md are
authoritative. This checkout remains the live push release source, not the group
migration source; do not deploy group migrations with this checkout's scripts.

The group branch contains V117–122, directory/canonical identity/device registration,
shared five-parent quota, encrypted provider generations/recovery and bounded
provisioning requests. Atomic native enqueue/retry, fresh-device publication,
bounded failure settlement and scheduling are now wired into shared Spring DI.
Both group feature/provisioning flags defaultfalse. No group HTTP/gateway/rooms or
Mobile ownership migration is exposed. Retained DEVICE activation remains denied
pending explicit runtime/private-key/store transfer; new group devices under a
retained canonical USER are supported.

Latest acceptance:14 actual API/worker cases PASS192.349s, full worker127 cases/
126 active/1 existing opt-in retention skip PASS315.901s,27 suites,8248 unchanged
source/build/resource inputs, format/architecture PASS. Seven real flag contexts
verify enabled claims, missing/default/each-false denial and resource termination.
Actual scheduled native registration through pinned Synapse publishes encrypted
credential+ACTIVE device+SUCCEEDED head+journal, verifies provider whoami, and keeps
replay unchanged. API runtime cannot claim the worker queue. All checks complete.
Earlier queue/schema/archive and native/worker race evidence is retained in that
branch. No failed run in this wiring unit.

Native group status now passes26 policy/actual-database cases in119.361s,8252
unchanged checked inputs and quality. It observes current native/group setup,
expiry and explicit retry timing without mutating queued proof after a refresh;
missing/foreign/late-denied metadata is rejected. The command is not yet wired into
DI/HTTP. Next retained runtime transfer and group gateway/rooms/coherent client
ownership. Read the branch handoff and ignored runtime-transfer-next.md before edits.

Mobile 3d85d569 is pushed: public SDK identity reads and the server-compatible
fingerprint pass root/Android/proof checks and 461 browser cases. Actual SDK 26.10.23
emulator proof passes in 74.319s: clean close/process restart retains the keys;
an empty store differs, a wrong passphrase is rejected, correct reopen still works,
and file ownership is exclusive and released after client close. The exact emulator
and owned fixtures are cleaned up. Mobile docs/collaboration-local-identity.md and
measurements/collaboration-local-sdk-identity-2026-11-03.json record all evidence and
earlier failed checks. No product caller, ownership transfer or new phone build.
Hosted CI for this commit is being tracked separately; actual Apple SDK behavior
remains open. Never logout losing/late Matrix candidates: Synapse removes
same-device winning tokens and E2EE keys. Transfer must fence all old gateway/
provisioning/direct-room provider callers and preserve the SDK store, private keys
and pending transaction IDs. All 54 communications acceptance groups remain open.

Live8b40bd0f/schema116 has chat push ON and SMTP OFF. Phone normal release28 remains
installed and verified; no reinstall is needed. Mobile0d96e737 records8273 gates:
native41701412608 (5 provider,230 Android-host,18 proof-host cases,3 inspected SystemUI
PNGs), control41701412602 (3110 portable,1215 Apple,full Xcode822s,432 checked hashes).
Actual Apple Matrix/APNs/CallKit remains open. Starter9959c1d needs no port for these
HRIS-specific changes. Continue the full long run, frequent user/Git updates and
push every atomic commit.

## Private chat push active; release 28 installed — 3 November 2026

Live API/worker now run accepted source8b40bd0f/schema116. All three chat push flags
are ON; SMTP remains OFF. Full Verify41695437208 passed all14 jobs with downloaded
artifact audits. [Rollout evidence](measurements/chat-push-private-rollout-2026-11-03.json)
and [operations](collaboration/private-push-operations.md) supersede earlier pending
deployment notes below. HRIS and nonempty Matrix checkpoints restored successfully,
with separate data/key storage and complete temporary cleanup. First activation
failed on an internal IP collision and rolled back healthy; pinned application IPs
and provider-first startup passed on retry in28.528s. Source identities, signing
key, crypto/message tables, Google/person/native/document state and schema history
match the checkpoint. Private trusted TLS reaches validation; direct/forged and
non-Synapse callbacks are denied. No real-user notification/message was generated.

Mobile37401799 records installed normal release28 (source8273ae8d), same signer,
UID/data retained, pulled APK hash806e23a770b52f71218e614d8a87af161af322c92d5852b0311929378d655374.
Do not reinstall it. c770 native/control runs41691878601/41691878650 passed with
downloaded reports and actual image review; new8273 runs41701412608/41701412602 are
in progress. Live push activation itself does not prove enrollment or notification
on the physical phone; ordinary Chat opening can enroll the installed client.

Continue feat/communication-space in .worktrees/communication-space:5debc632 holds
the stable directory foundation/V117, not deployed. Bounded directory repository
and tests are WIP there; data1 passed76.781s/3 cases, format/architecture,3583 source
hashes unchanged. Review and commit that increment. No group native/device/room API or mobile
ownership migration is accepted yet. All remaining chat/call/video/meeting scope
continues. User requires frequent updates, Git handoffs, every commit pushed and
validated future builds installed with notice. No generic Starter port is needed
for the HRIS-specific empty-history UI or private runtime configuration.


## Full push release gate accepted; live switch pending — 3 November2026

Full Verify41695437208 at8b40bd0f passed all14 jobs. Digest-verified evidence:
[release CI](measurements/chat-push-release-ci-2026-11-03.json). API3161 cases across
438 suites,6 opt-in skipped; worker117/24 with1 elapsed-retention opt-in skipped.
No failures/errors; API class ownership is disjoint across8 shards. Seven browser
reports pass with zero flaky/skipped:278+278+277+278 unit/browser cases,41 actual
API integration,14 actual worker integration,1 export integration. Recovery schema116
restores104 objects/2098795bytes,2 identity envelopes; all cleanup/unchanged-source,
retired-key rejection and metadata/history/RLS flags pass. Separate actual product
FCM run16 and Mobile QUEUED/FROZEN regressions remain below; full Verify does not
execute its six opt-in native/provider methods.

Accepted JARs downloaded under .work/chat-push-rollout-2026-11-03/packages:
API SHA f487ffd39fbee7f28d3fa33064fdb9d4b6ea0b7098c7cd65caa9b7ae538100e7,
worker430620d7e64614db7524de60f0edbe09914e71917ab640542265457ade960b1c.
Existing build/libs untouched. stage-images.py, checkpoint.py and deploy.py are
prepared/reviewed/syntax-checked but NOT executed. Their hashes are in the evidence.
Checkpoint now captures the nonempty provider's actual mounted config, encrypted
identity/device/message fingerprints and signing key, with coordinated HRIS backup
and isolated restore; cleanup/source restart paths are explicit. Deploy uses exact
accepted images and private TLS peers, verifies trusted malformed-body400 plus
untrusted direct/forged-forwarding/proxy403, and restores config/images on failure
without restoring old data over new messages. Preserve old operator material.

Live remains304be027/schema116, push flags/SMTP OFF. Wait for current Mobile build28
session93460 before another local heavy job. Mobile8273ae8d is pushed: empty-history
UI fix,297 tests/4 inspected PNGs. Phone remains27 until normal build/verify/smoke,
notice and install-only28. The c770 control41691878650 is now accepted in Mobile
.work/chat-history-local-reuse-control-2026-11-03:3109 portable/1215 Apple cases,
zero failures/errors/skips,432 hashes, full Xcode. Native41691878601 still runs.
New8273 native41701412608/control41701412602 queue independently.

Separate worktree /home/fajar/hris/.worktrees/communication-space branch
feat/communication-space pushed5debc632: V117 directory with33 database/12 recovery
tests; minimal role preserves HR RLS and old identities. Seven bounded directory
repository/entity/datasource/test files are WIP there, formatted and architecture
checked but not compiled/tested, not wired to API/DI. Do not deploy that worktree
for this release. Continue group ownership/mapping, real ACKs, rich chat and all
voice/video/meeting work after activation. No stopping at a partial milestone.

## Current product push accepted; full release gate repaired — 3 November2026

Actual product FCM run16 PASS271.055s/driver209.335s with Mobilec770a83f product6/peer6,
Platformd00ac5f5, fresh1/0/0/0. Normal login/Chat UI, Firebase-caused restart, actual
SystemUI encrypted reply, registrations/pushers/delivered1/1/1, all31 admission flags,
one callback/revision/attempt and full independent cleanup. Five PNGs viewed; three
SystemUI frames retained in docs/images/product-chat-fcm/current-*.png. Evidence:
[latest product](measurements/product-chat-fcm-current-ui-2026-11-03.json).
Mobile also proves both QUEUED/FROZEN_DRAFT native regressions:645.323s, two fresh
passes,176 encrypted originals/11 incoming reopens/11 foreground pages each; source
Mobile3824/Platform4325 hashes unchanged. Phone27 normal build is running from Mobile
8ac28f68 (documentation after c770), session94636; phone remains26 until normal
verify/smoke/PNG/preflight/notice/install-only. Never install product6/peer6 on phone.

Full Verify41692502923 atd00 is rejected for dashboard shard4:276/277 pass, optional
MFA dialog Escape occurs while aria-busy=true. Screenshot/trace and digest-verified
artifact13258595649 retained. Nine local assurance browser cases PASS19.768s after
waiting for aria-busy=false before dismissal; format passes. Application/runtime
code is unchanged. [Evidence](measurements/verification-dialog-readiness-2026-11-03.json).
Dispatch a replacement full Verify after this commit; its concurrency rule will
supersede the known-failing earlier run. Do not accept d00's partial full run.

Private ingress candidate staged (not activated) at ~/.config/hris/chat-push-pilot.
Same Matrix server name/config/keys; only a new private CA/leaf, SSL trust bundle,
exact proxy172.25.0.6/32 and Synapse172.25.0.3 peers. No new host ports. Active env/
overlay/provider files unchanged. nginx -t passes as UID10001 with read-only root,
ALL capabilities dropped and bounded tmpfs; first default-cache-dir failure retained.
[Staging](measurements/chat-push-ingress-preparation-2026-11-03.json). No proxy runs.
.work/chat-push-rollout-2026-11-03 holds intent/inventory, prepare-ingress.py and CI/
package collectors currently targeting d00/run41692502923. RETARGET only to the new
accepted full gate. stage-packages.py preserves build/libs and has no fixture
exclusions. Image staging/deploy/checkpoint scripts have NOT been copied/retargeted.
Reuse reviewed prior rollout methods with fresh HRIS and separate Matrix backups,
restore validation, old-image rollback and socket/TLS/public callback denial checks.
Live API/worker304be027/schema116, all chat push flags/SMTP OFF; preserve known live
containers, Google/native/document state and unrelated Docker services.

Mobile current c770 control41691878650 running, native41691878601 pending; old537
native41685363689 still executing the unfixed pager. Apple537 full CI41685480887 is
accepted1104/124,419 hashes, full Xcode1318s; c770 still needs its matching control
Apple result. Old537 control41685363392 has the expected, artifact-verified browser
Asia/Jakarta failure, fixed bya59d3cd2. All remaining communication groups continue:
cross-company stable account/space migration, real recipient ACKs, rich chat, real
Apple/web SDK, calls/video/meetings. Do not stop at phone27 or push activation.


## Actual product FCM and inline reply passed; current UI build continues — 3 November 2026

[Evidence](measurements/product-chat-fcm-session-admission-2026-11-03.json) /
[contract](collaboration/product-fcm-acceptance.md). Run15 PASS266.651s, fresh1/0/0/0
JUnit, source unchanged; driver207.402s. Actual product native login/setup registers
FCM and managed pusher. Peer encrypted send follows ordinary product death; Android
attributes the new process to Firebase. Real SystemUI notification/Reply returns
an encrypted reply to the peer. Counts1/1/1; wake revision1 delivered/visible with
one attempt. All five PNGs actually viewed; three notification/editor/ack PNGs tracked.
Both cleanup flags true, fixture credentials gone, owned emulator independently absent.
No phone/live mutations. Fixture d480550b-54b8-4bf1-95e5-5092a0685713, private evidence
in.work/product-chat-fcm; wrappers/results under.work/product-chat-fcm-2026-11-03.

Root cause: the push policy required authentication at/after session creation,
but legitimate native issuance retains earlier authentication. Remove only that
invalid ordering check; future time, credentials/revocation/ownership and other
chronology checks remain. Two domain and one PostgreSQL callback+worker regressions
pass. Broad check544.699s, unchanged inputs:57 domain,150 data,117 worker (one optional
elapsed-retention skip),two server =325 passed/one skipped. Rebuilt worker SHA
430620d7e64614db7524de60f0edbe09914e71917ab640542265457ade960b1c.
Run14 before fix had parsed expected-room callback200 and all observed guards true
except authentication order, leaving hints empty; all three PNGs viewed. Run13
was a diagnostic nullable-map compile failure, no fresh JUnit; fixed before14.
Test-only probes expose bounded flags/counts, never keys/tokens/bodies.

Run15 uses immutable Mobile1136 artifact5 (product SHAe05999a372674776628e89a7dc5e8e9450dc1c20e7c31c8beb40736de02d7662;
peer SHA9ccd3b6b50d98543b77308e3648af8dcd4ebd1f3ca7aad98b295b83dbb97ad9b).
New UI still needs artifact6 and its own product run before deployment/push enablement.
Mobile artifact6 builders/verification prepared; not run. Keep one local heavy job.
Current Mobile browser check session91254 is active: fixes test-only named-timezone
module setup found by actual CI (bd399 control41677393371 failed Asia/Jakarta;
application already loads the module). Do not edit Mobile inputs while it runs.

Mobile537b89ae is pushed: bounded owned offline list previews/drafts and localized
timestamps, full672.455s check (4037 desktop,230 Android host,18 proof-host,10 logic;
1805 fresh cases; product lint0; three final PNGs viewed). Physical phone still26,
installed16:56:36UTC with UID10962/signer/data preserved; do not reinstall26.
Release27 helpers are prepared and require clean source, checks, emulator smoke,
images and notice immediately before install-only; never drive phone UI.

bd399 full Apple41677479513 is accepted (1097 cases, full Xcode1258s,410 hashes).
Its Native41677393348 attempt2 is running; attempt1 emulator archive failed before
native execution. New537 Apple41685480887 and Native41685363689 are separate gates.
Audit archives/source/report/PNGs before acceptance. Physical iPhone remains waived.
Runtime304/schema116/dashboard2124c6fc and chat push OFF; Starter9959c1d unchanged.
Keep the user updated, push every commit and continue all54 PRD groups: actual ACKs,
group cross-company migration, rich chat, encrypted backup, calls/video/meetings.


## Product FCM callback retains no wake hint — 3 November 2026

[Run12 evidence](measurements/product-chat-fcm-empty-hints-2026-11-03.json): fresh
owned run FAIL320.655s, driver252.355s, unchanged inputs, one executed JUnit case.
Actual application login/device/native registration/managed pusher succeed. Encrypted
peer original reaches Matrix and callback returns200, but hints table is EMPTY.
Synapse retains one pusher with no failure. This is before queue/FCM delivery, not
proof of a provider/token problem. Counts1/1/0, all independent probes available;
all three settings/chat/home PNGs actually viewed and owned cleanup/secrets removal pass.
New fixture-only probes map unavailable reads to a safe class name and do not expose
keys/tokens/bodies. Need inspect callback admission/expected room under actual product
identity next, then fresh run with matching fingerprints; do not relax authority.

Mobile b5ac30f5 records release26 installed16:56:36UTC, unchanged UID10962/signer/
data and exact pulled APK21d134bc1e1cbae5e4fdf242da450f1318bdab07c6a5c3568740a7586a540cf4.
No phone UI driven. New offline inbox previews are WIP and focused Mobile tests now
own local heavy capacity. Runtime304/schema116/push OFF; all54 groups stay active.


## Chat demo accounts completed; UX and group scope in progress — 3 November 2026

[Demo evidence](measurements/chat-demo-accounts-2026-11-03.json): the four existing
SAMPLE employees had no accounts; the company had only two eligible chat members.
All four now have distinct fictional accounts/password hashes, chat-only grants
and normal API person bindings. Six company members now have chat; each of the
four demo logins successfully reads five other eligible contacts. Owned temporary
sessions revoked. No message/provider enrollment, Firebase mapping or phone change.
The private manifest/passwords live in.work/chat-demo-2026-11-03 and must never
be committed. First apply's profile field mismatch was corrected before any link,
then resumed idempotently. Accounts were administrative demo seeds; grants/person
links used the normal APIs. Real HRIS PG is1e023349da86cba6106f5131a4a06d7e5de4884710e316919b8c2072e8d03f22;
1c63ff44261f is MATRIX PG, not HRIS. Preserve both distinct databases.

The user explicitly says these UX complaints do not cancel the long-running full
PRD. [Cross-company amendment](collaboration/cross-company-chat.md) is committed;
company-bound discovery alone cannot implement it. Mobile's primary Chat tab,
compact bubbles/day separators/composer and restored exhaustion pass156 focused
cases and viewed light/dark-large-text/landscape screenshots. Full check3 is active
under Mobile.work/chat-ux-2026-11-03; no parallel heavy local Gradle/emulator job.
Native41667296924 still runs at1136. Apple41667309251 attempt2 now ACCEPTED:
1097 tests/123 suites,397 source hashes, full Xcode1202s; artifact13250850483 SHA
d354a19dc0bf94cdf5a3fe7a07db41202405ac2cf776961182c90674db8312a1. New UI source
needs its own Apple gate. Next validated phone26+ with notice/install-only.

Product FCM run11 reached real encrypted send and callback200 but delivered0.
New UNCOMMITTED fixture diagnostics capture provider pusher failure/count and
bounded hint revision/attempt/lease fields in push-state.json; not yet executed.
Complete that next provider attempt after Mobile checks/builds free local capacity.
Actual FCM/inline reply, real delivered/read UI, inbox previews, group admission,
rich messaging, voice/video/meetings and all54 groups remain active. Runtime304,
schema116/push OFF and Starter9959c1d remain unchanged.


## Product FCM clock fixed; user chat UX priorities active — 3 November 2026

[Clock/attempt evidence](measurements/product-chat-fcm-clock-2026-11-03.json).
The independent worker uses wall time; API fixture now explicitly follows it.
Default fixed test clocks remain unchanged, with a passing mode/zone regression.
One sampled loginAt keeps JWT lifetime at exactly60 seconds. Run10's rejected
fixture login is preserved. Run11 passes normal setup, joins/keys and product
managed-pusher registration; actual encrypted peer send succeeds. Counts1/1/0:
FCM delivery/process restart still fails after313.673s (driver250.343s). Both
cleanup results pass, owned process/image/credentials absent. Chat-ready and home
failure screenshots actually viewed. No live/phone change; push stays OFF.
Next inspect private callback/worker delivery using bounded safe metadata. Never
upload raw logs or credentials. No local heavy job remains active.

Latest user priorities do NOT cancel the full communications PRD: bottom Chat tab,
local-first rendering, compact bubbles/composer with true delivery/read states,
restored history exhaustion, and employee discovery including cross-company chat.
The explicit cross-company request supersedes the original company-only CHAT-01;
implement scope and membership changes in API/RLS/provider ownership as well as UI.
Mobile is editing navigation/UI now; preserve that work. Phone25 stays installed,
next validated26+ with notice/install-only, no phone UI driving. Apple41667309251
attempt2 passed simulator tests and is building Xcode; Native41667296924 is running.


## Product FCM run8 reaches the thread; diagnose unavailable gateway/worker — 3 November 2026

Mobile1136fb9e529faa2d5ad2f3dc02bf8c7fb326e298 is pushed. Application managed-push
registration and setup/native-status wakeups pass full root/Wasm/product+proof/lint
495.015s (4,015 desktop,230 host,18 proof-host,ten logic;2,421 fresh). Clean loopback
build5 PASS45.425s. Source-pinned product5/peer5 artifacts are in Mobile.work/product-
chat-fcm-2026-11-03/artifacts5.json; NEVER install these on a phone. Phone25 unchanged,
normal26 prepared but not built. Apple41667309251 queued, Native41667296924 pending;
prior checkpoint Native41662285409 still runs. Audit each exact source separately.

Platformb7ee008f corrects transient boot probes within the original100-second budget;
24 guards pass. Run7 boot failure/black PNG and cleanup remain recorded. Run8 now boots
31.569s, passes native login/FCM registration and chat activation, enters the peer thread,
then pusher wait fails. Thread shows service unavailable; cause NOT ESTABLISHED. Counts
1 registration/0 pushers/0 delivery, no encrypted message sent. Run204.288s/driver155.303s,
both cleanups pass; PID2430275/image/sidecars/credential absent, two PNGs actually viewed.
[Evidence](measurements/product-chat-fcm-boot-2026-11-03.json).

Diagnostic increment reuses existing bounded gateway route/status/error trace (no faults
armed) and independently captures private bounded SDK/OS/job logs before cleanup. Never
print/upload raw private logs or fixture JSON.25 Python guards and testClasses/workerJar/
format/architecture PASS35.569s, all source hashes unchanged. Commit/push this increment,
then run9 from.work/product-chat-fcm-2026-11-03/run8.py with fresh output names and the
same artifacts5. Inspect sanitized gateway codes and worker states before changing
application behavior; no direct fixture invocation may bypass normal app activation.

No heavy job currently active. Read-only review confirms SDK endpoint derives the app's
API origin and enterTimeline starts actual sync; no confirmed endpoint/caller failure.
The test uses MutableTestClock, but background admission converts server duration to a
local deadline; clock skew is only an unproven hypothesis. Capture evidence first.
Live runtime304/schema116/push OFF and Starter9959c1d unchanged. API ID
488578f2375110e73b78e705e844c101601cf5e6706860f8b6fe4aa8cf2491fc, worker
638c61567561b98bbb870749986c80614cece41a4b2c8231bea7a092b2afec46; Matrix identity intact.
Finish actual FCM/new-source gates, validated normal phone26+ with notice/install-only,
then continue all54 groups. No phone UI or real employee tests; voice/video/meeting UI absent.

## Product FCM fixture committed; application activation gap next — 3 November 2026

The owned fixture/driver/real-worker implementation is now committed with20 passing
Python regressions and compile/format/architecture PASS32.929s; source hashes unchanged.
[Evidence](measurements/product-chat-fcm-fixture-2026-11-03.json) preserves all six
attempts. This is **not accepted end-to-end FCM delivery**. Run6 passes fictional native
login, one FCM registration and Chat is ready, then UI setup times out. Four PNGs
actually viewed; owned peer/emulator cleanup true. Run5 emulator hang and failed
combined cleanup remain rejected; later process/image/secret-file absence is recorded.
No encrypted message has been sent by this fixture yet. No live/phone changes.

The driver now uses snapshot-based activation, exact thread navigation, independent
failure captures and sanitized stack frames. Peer result reads use shell exit status;
only an exact missing atomic marker is pending. Never install loopback product APKs
on the phone. [Contract and failure detail](collaboration/product-fcm-acceptance.md).

CONFIRMED PRODUCT GAP: Mobile ActivateCollaborationPusher is bound/tested but has no
production caller. Mobile is implementing a scoped finite registration worker and
wakeups after chat activation/native notification registration. Do not bypass the
application by invoking pusher registration inside this fixture. Rebuild/pin the
updated product and peer APKs before run7; the existing product.apk lacks integration.

Mobile0a452afb is pushed: corrected intermediate notification checkpoint proof,
local native100.163s/raw16/runtime4/three deaths/five actions/three viewed PNGs; full
root127.791s passes3997 desktop/230 host/18 proof-host/10 build-logic including reused
reports. Native41662285409 at0a452afb is IN PROGRESS; old41650906105 remains rejected.
Apple41651053921 accepts unchanged a3f common source, but NEW activation code will
need matching full Apple/Xcode. Phone25 is installed/pull-back verified; next26+
with notice/install-only, no UI driving. Starter9959c1d unchanged.

Platform runtime304be027/schema116/dashboard2124c6fc remains unchanged, all chat push
OFF. Preserve Matrix identity/keys/checkpoints and Google/native/document state.
Continue all54 PRD groups; voice/video/meeting UI absent. Push each atomic commit and
continue long run. No generic Starter port for HRIS policy/test fixture.

## Current cross-repository checkpoint — 3 November 2026

Mobile **release25** at a3f39be7390874cf04b807b9abad365393530852 is installed and
pull-back verified on10.77.77.3:5555. Original signer/appId10962/first-install retained,
no phone UI launch/data clear. Do not reinstall25; future26+ after prior notice and
install-only. [Current Mobile handoff](https://github.com/fajarxfce/hris-mobile/blob/feat/communications/docs/agent-handoff.md)
is authoritative; older phone18 entries below are historical. Reviewed encrypted drafts
and explicit confirmed replay pass local root/IndexedDB; Apple41651053921 accepts
1,094 simulator tests plus full Xcode. Native41650906105 is REJECTED at an intermediate
recovery assertion; Mobile fixture correction is being validated, not yet accepted.

Platform runtime304be027/schema116 remains unchanged/live, dashboard2124c6fc unchanged.
All chat push flags/SMTP remain OFF. Preserve Matrix identity/keys/checkpoints, live
Google/native/document state and all rollback images. Starter9959c1d unchanged.

UNCOMMITTED fixture WIP: ProductChatFcmInteropTest/ProductChatFcmWorker and
collaboration_product_fcm.py/device.py/tests. Real private FCM credential is consumed
only by an independently owned worker; fictional PostgreSQL/Synapse/TLS/account data,
actual product devDebug receiver, separate encrypted SDK peer and disposable AVD image.
No Firebase test users or real employee messages. [Fixture contract](collaboration/product-fcm-acceptance.md).
Compile1 PASS40.111s (test classes, actual worker JAR, format/architecture/eight initial
Python checks). Ten Python cases now pass after clickable-label/FCM-start guards.
Mobile x86 product/peer APKs compile with18 proof-host cases; new fixture source is
still UNCOMMITTED/NOT ACCEPTED. No generic Starter port is needed.

.work/product-chat-fcm-2026-11-03 retains run1 SKIPPED (sudo stripped opt-in), run2
NOT EXECUTED (Gradle reused skipped report), neither is acceptance. Run3 now running
with explicit environment after runuser and task-specific --rerun; no parallel heavy
job. Audit real execution/counts, process/fixture cleanup and actual screenshots.
Product APK targets loopback18084 and MUST NEVER be installed on the phone.
After fixture acceptance, complete Mobile native checkpoint/full quality/hosted gate,
then continue full communications PRD. All54 groups OPEN, voice/video/meeting UI absent.

## Read-receipt gateway rollout — 2 November 2026

API/worker now run accepted304be027 at unchanged schema116. Full Verify41475356301
passes all14 jobs:3159 API cases (five existing optional skips),117 worker cases
per shard (one existing optional skip),1111 mocked browser and56 actual integration
cases; all report zero failures. All artifact digests and recovery verification pass.
Exact CI JARs and built-image contents were verified before replacement.
[Evidence](measurements/chat-read-receipt-rollout-2026-11-02.json).

Fresh checkpoint dev-before-read-receipts-20261102T163839 and separate key pack
verify201 relations, two identity envelopes, three objects/2222 bytes. First attempt
aborted before image changes because runuser inherited Java17; old services restored
and health passed. Retry explicitly selects Java21 after runuser. Capture, recovery
verification and replacement pass23.375s. Use explicit JAVA_HOME and PATH for future
recovery commands; retain the failed attempt evidence and never retry blindly.

API/worker start as10001:10001, zero restarts, exact images, API healthy. Google/person/
native/document fingerprints and entire migration history match the stopped checkpoint.
Dashboard2124c6fc index unchanged. Existing HRIS environment, Matrix identity/config,
Matrix/database containers and old rollback image tags preserved. All three chat push
flags and SMTP remain false. No provider account, message, grant or phone interaction
is part of this server check. Matrix recovery remains separate from HRIS backup.

Mobile release18 from clean42f5d157 is installed on10.77.77.3:5555 with exact APK
pull-back verification, same signer/appUID/first install, no UI launch/data reset.
Adds private visible-read marks and bounded replay; all54 groups remain open.
Continue privacy/mute, notification actions/inline replies and the remaining PRD.

## Native local read-intent recovery accepted — 2 November 2026

[Evidence](measurements/chat-read-receipt-replay-native-2026-11-02.json) pins pushed
Mobile ae4a1b27 (ae4a1b270d50b7edd7761903a316e033dec70972). Its common receipt runtime contract and bounded sender
validate retained coverage/history, fresh HRIS device/platform/conversation/room
before SDK entry and again after provider success, then ACK only the identical
queued intent. No hidden retry/coalescing; denied or uncertain work stays retained.
Local read/mark admission a349c84e and encrypted queue875edab6 are also pushed.

Actual HRIS/PostgreSQL/Synapse/Android113.216s PASSES with133 Mobile/25 Platform
source hashes unchanged. Provider success followed by interrupted app ACK leaves
exact read coverage and pending intent through real OS death. Next process replays,
ACKs, reopens and repeats without another send. Seven receipt POSTs return200
(five private/two public); existing private isolation, three encrypted messages,
all three process-death stages and owned cleanup still pass. Fixture
.work/collaboration/37854341d48f45a0b43dc56ca73b985b.27 driver tests pass0.104s;
missing/non-Boolean application recovery claims reject. Only three Platform test/
driver inputs change; no production server changes beyond pushed8c87839a gateway.

Mobile full root/proof/browser292.360s passes3299 desktop/230 Android host/10
build-logic/six proof-host/12 browser cases including explicit reused tasks;
1732 cases/222 suites fresh,0 failures/errors/skips. Focused133.705s12 domain/two DI
cases passes. Owned emulator5562/PID790095 boot26.497s, proof removed, stopped1.905s;
no emulator/heavy job remains. Phone17 and live dac6fb0d/schema116 unchanged, all
chat push OFF. Current receipt gateway is not yet rolled out to the live pilot.

Commit/push this counterpart, dispatch Mobile full Apple/Xcode, then continue
worker/foreground receipt integration, privacy/mute, notification actions/inline
reply and all54 groups. No generic Starter port. User explicitly requests the next
validated HRIS product build on10.77.77.3:5555 too: notify first, --install-only,
retain signing/data and never drive their UI. Chat UI exists in17; voice/video/
meeting UI is still absent. Latest crash-fix confirmation concerns11, not17 chat.
Do not end at this checkpoint; continue long run with a push for every commit.


## Mobile local read queue accepted — 2 November 2026

Mobile875edab6 is pushed with exact encrypted read markers and a128-intent queue.
[Counterpart evidence](https://github.com/fajarxfce/hris-mobile/blob/875edab6/docs/measurements/chat-read-state-2026-11-02.json):
11 actual SQLite and11 IndexedDB storage cases pass; full root161.148s covers3273
desktop/230 host/10 build-logic cases with explicit reused results. Five browser
transport cases also pass25.118s after correcting an existing test's unsynchronized
MockEngine cancellation flag; no HTTP production change. Initial replaceable-intent
draft and the failed broad attempt are retained. Existing history key protects the
new namespace; no schema/key reset or age eviction. Other pending read points are
preserved until exact acknowledgement; timestamp-based supersession is disallowed.

Local admission/mark and live-admitted queued replay are next, followed by notification
mute/privacy/actions/replies. A privacy change must suppress/cancel queued public
receipts explicitly, without inventing server ACK. Common runtime receipt methods,
UI/worker consumption and new full Apple/Xcode coverage still need work. No Starter
port. All54 groups remain open. Live API/worker dac6fb0d/schema116, chat push OFF,
phone17 unchanged; private identities, keys and Google/native bindings retained.
Continue long run and push every commit; storage acceptance is not task completion.


## Actual Android read-receipt proof accepted — 2 November 2026

Mobile c0ad592f is committed with finite native receipt operations and eight host
lifetime cases. [Native evidence](measurements/chat-read-receipts-native-2026-11-02.json)
binds114 Mobile/25 Platform inputs: one real HRIS/PostgreSQL/Synapse/Android SDK
scenario passes109.111s. Five empty receipt POSTs (three private/two public) return200;
peer-private isolation and retained own SDK metadata in the third OS process before
sync pass. Existing bidirectional encryption, three original message sends, held-room
isolation and acknowledgement/completion recovery across three process restarts pass.

Four counterpart fixture/driver inputs now require these facts, accepted receipt
routes and strict boolean evidence. Python26 regressions pass0.159s; native invocation
also passes format/architecture. Mobile full root/proof build275.337s passes3262
desktop/230 host/10 build-logic/six proof-host cases, including explicit reused results;
1683/218 suites freshly executed. Initial focused build failed only101-line proof
method after eight passing cases; helper extracted before full/native success.

Owned emulator5562/PID705941 stopped in1.604s, proof package absent. APK343,275,325B
SHA2564bd9200487608c1efb258d29ddb3762e92bfe32079934307010fb690254cd035. No phone UI,
FCM or Apple provider claim. Receipt gateway8c87839a is pushed; live pilot remains
accepted dac6fb0d/schema116, all chat push OFF and phone17 unchanged. Preserve Matrix
identity/config/checkpoints, original Google/native/document state and old images.

Next: expose Mobile receipt operations through common runtime ownership, implement
encrypted local read intent/state and admitted replay, then mute/privacy/account-bound
notification actions/inline replies. Continue all54 groups; none closes here. New
common changes require full Apple/Xcode coverage. This Matrix/HRIS increment has no
Starter port. Push every commit and continue long run; no task-completion claim.


## Read-receipt gateway accepted locally — 2 November 2026

[Contract](collaboration/read-receipts.md) / [evidence](measurements/chat-read-receipts-2026-11-02.json):
17 source/test inputs add exact public/private metadata POST with owned event proof,
fresh admission around external I/O and bounded self-private/pair-public sync
projection. Scope v2 re-registers the receipt filter without changing stored keys,
commands or independent history cursors. No schema change.

Focused99.155s passes55 domain/13 data/14 delivery/one HTTP case. Broad809.053s
passes275 data/34 actual HTTP/117 worker cases (one existing optional skip); domain
and delivery are up-to-date. Zero failures/errors, format/architecture pass. Original
collector mixed epoch and monotonic origins; preserved report mtimes were re-audited
against UTC start, excluding16 reused suites from fresh broad counts. Actual owned
Synapse receipt privacy/repetition/no-backward proof passes7.216s; synthetic encrypted
envelopes do not prove native SDK/decryption. All input/report hashes retained.

Mobile .work/chat-read-receipt-client-2026-11-02 contains uncommitted native adapter,
eight host lifetime cases and actual SDK proof extension. Focused check1 is running;
follow with full root/proof build and owned real HRIS/SDK proof before acceptance.
Do not run concurrent heavy builds. Continue durable read state, notification mute/
privacy/actions/encrypted replies, then all54 groups. No whole group closes here.

Live API/worker stay dac6fb0d/schema116, dashboard2124c6fc, all chat push OFF; installed
phone17 is unchanged. Preserve private Matrix identity/keys/checkpoints. No new phone
UI acceptance. User confirmed release11 opens; install-only17 was independently
verified. Push every atomic commit and continue; this is not task completion.


## Private chat pilot deployed — 2 November 2026

[Full CI](measurements/collaboration-full-ci-2026-11-02.json) 41454857226 COMPLETE at
exact dac6fb0d17ad8b8b3226a4fbb93233bd315a1d7d: all 14 jobs pass. Downloaded artifact
digests verified. API 3,158 cases/436 suites (3,153 active, five existing optional
skips), worker 117/24 per shard (116 active, one existing optional skip), no failures
or errors. XML class ownership is disjoint across eight shards; parameterized display
labels legitimately repeat within classes. Browser 1,111 mocked scenarios and 41
actual API +14 worker +one export workflow pass, with zero flaky/skipped cases.
Schema116 recovery verifies 104 objects, two identity envelopes, separate keys and
wrong-key rejection. Both prior failures remain archived; do not rerun accepted CI.

[Private rollout](measurements/chat-private-rollout-2026-11-02.json) COMPLETE:
verified API/worker JARs and non-root images, fresh coordinated checkpoint 11.468s,
isolated103→116 clone 17.022s, then explicit live migration/API-worker/dashboard
23.204s. API/worker now dac6fb0d, schema116. Dashboard immutable2124c6fc source matches
the accepted tree and index ec8186f22fde67438a6430d4d69aff3adcff018cbdb2845139b7692ec777091c.
Google/person bindings, active native session IDs, document revisions/counts,
original migration prefix and restricted roles preserved. Existing HRIS environment
preserved; SMTP and all three chat push flags OFF. API/worker use both default and
internal Matrix networks; stable matrix.hris.internal identity/keys remain.

Fresh HRIS checkpoint: ~/.local/state/hris-platform/recovery-data/
dev-before-collaboration-20261102T141953; separate matching recovery-keys directory.
Old API/worker tags retained, exact names in measurement. Matrix's separate empty
pre-enrollment checkpoint pilot-20261102T123710 remains; ordinary HRIS recovery pack
does not include Matrix. Keep the protected fifth Compose overlay
~/.config/hris/matrix-pilot/compose.matrix.yml and every preexisting overlay/keyring.
Do not regenerate provider identity/config, rerun migration scripts or prune volumes.

Existing employee/operator memberships gained only collaboration.chat through live
native/MFA/versioned commands, retaining all applied roles and permissions. Company
policy already enabled the module. Contact/device/directory reads pass; no enrollment
or human message. A second operator login in the same TOTP window initially returned
invalid_mfa_code; its owned partial session was revoked. Next-window retry passes
without relaxing MFA. All owned operator sessions are revoked; Google binding unchanged.

Actual native priority counterpart Mobile a212bd2a and Platform bc9774eb are pushed:
108 Mobile/seven Platform inputs, three queued/frozen/cancelled variants pass with
actual Android SDK/HRIS/Synapse. Oldest-of175 priority uses one turn/12 originals,
retains global wake and four progress positions, then normal recovery retains176
originals over11 reopens. See native-priority measurement for every attempt/hash.
Owned emulator5562/PID553348 is stopped and proof package removed. Apple41446374520
atcc4c11f1 is fully audited (112 native core cases,106 source hashes/full Xcode).
Starter0d4432e generic backup port is independently accepted/pushed.

Mobile clean c6347c75 release17 is INSTALLED and pull-back verified in62.534s,
SHA256b94aedf2b1eb81949d8eeacc82fd2906f0c85b27641e3bcc65fd8884e5fb34d2,87,495,300 bytes.
Explicit10.77.77.3:5555 --install-only after notice; UID10962/first-install retained,
non-debuggable, no uninstall/data clear/UI driving. Mobile4f1531a6 records delivery
and compatible server. Actual-artifact build/lint/two emulator cold starts pass;
no new physical phone Google/chat/FCM acceptance. Future phone build must exceed17.

Next implementation is staged ONLY as .txt in .work/chat-receipts-2026-11-02:
17 baseline-hashed inputs add exact public/private receipt POST, owned event/read
admission before external metadata write, receipt-only sync projection with private
self filtering, bounded shapes, and v2 filter scope. Includes data/actual provider/
HTTP/revocation tests, none compiled or executed yet. apply.py requires successful
runtime rollout and clean checkout; commit this handoff first, then apply, format,
run focused checks, inspect failures, broaden relevant checks and commit/push only
accepted code. Staging scripts predate .txt renaming; do not rerun them blindly.
Continue actual Mobile receipt/mute/privacy/action integration and encrypted inline
replies, then all54 open groups. This pilot/install is not task completion.

## Full CI reached API time budget; eight shards prepared — 2 November 2026

Replacement41448616746 at2334e266 FAILED by the35-minute Gradle step timeout in
backend1. Seven jobs succeed; dashboard/recovery integration skip. Its worker HTML
is complete:117 tests, zero failures, one existing elapsed-retention skip,6m58.71s;
all six provisioning cases pass47.756s and the corrected outage case passes5.770s.
No completed API report exists for shard1, so the slow/hung case is not established.
Successful shard2 has695 API cases, zero failures/four existing skips,27m25.94s before
adding build/setup time. [Retained evidence](measurements/ci-api-shards-2026-11-02.json).

Only CI configuration/convention logging changes in this atomic commit: eight API
shards using the existing class-owner algorithm, same35m step/40m job deadlines,
started/failed/skipped test events, XML alongside HTML artifacts. Shared modules and
worker checks remain complete per shard; ApiContractHttpTest still belongs to1.
Full workflow now requires14 successful jobs. Local format/architecture/API test
compilation with1/8 passes165.774s on two unchanged inputs. No local API execution
or performance improvement is claimed. Push/dispatch one full run at committed head,
then retarget all prepared rollout source/run/job-count guards; do not reuse failed
2334e266 acceptance. Current immutable dashboard tree remains equal.

Four native-priority test/driver inputs in Platform are UNCOMMITTED. Eighteen driver
regressions pass and the two Kotlin fixtures compile. To permit independent native
proof work, stage-packages/checkpoint/deploy source comparisons now exclude exactly
NativeSdkGatewayTrace.kt and NativeCollaborationWorkerInteropTest.kt; all packaged
runtime inputs must still equal the accepted CI head. No broader exclusion. The
native proof has NOT executed. Its .work/native-priority-2026-11-02/run-native.py,
inputs-1.json and apply scripts are prepared. Run single cancellation case first,
then remaining two variants; preserve source/artifact hashes and failures.

Mobile full native-proof build3 PASSES97.046s on108 inputs:3262 desktop,222 host,
10 build-logic/six proof-host cases, no failures/errors/skips. Four proof source files
remain uncommitted (including the obsolete company-fixture constructor repair).
Actual company WorkManager burst/stage/process-death resume passes45.199s using the
new proof APK, with no SDK/provider claim. Release17 actual arm64 APK also cold-starts
twice on owned x86 emulator via libndk_translation,33.918s; onboarding screenshots
reviewed, no login/chat UI or phone interaction. Owned emulator is STOPPED after the
second startup (latest owner534984). It must be freshly started before native proof.
Private Matrix healthy/pre-enrollment retained; HRIS still103, no checkpoint/deploy/
grants, phone still15. Continue after dispatch: actual SDK proof, guarded rollout and
install-only17, then notifications/replies and all54 open groups.

## Release17 ready; latest Apple audit complete — 2 November 2026

Mobile fe485267 records the clean c6347c75 devRelease17 candidate: build/lint433.769s,
zero lint,87,495,300-byte arm64 APKb94aedf2b1eb81949d8eeacc82fd2906f0c85b27641e3bcc65fd8884e5fb34d2.
Same signer/private API/Firebase and16KiB alignment; compiled backup exclusions verified
through shortened release resource path. Phone read-only preflight confirms15 and same
signer. NO INSTALL. Starter0d4432e has the independently accepted generic Android XML/
manifest port; root/resources/lint70.128s,356 desktop/six build-logic,44 existing warnings.

Apple41446374520 atcc4c11f1 COMPLETE/audited:112 native core cases/30 suites, no failures/
errors/skips;106 combined source hashes, Swift11s, compile740s/tests127s/Xcode1436s,
associations1s. Shared priority compiles in the app; actual priority behavior still needs
Android SDK proof. Mobile's build17 measurement contains exact artifact/XML/source hashes.
No physical iPhone or FCM/OS acceptance. No duplicate Apple dispatch required.

Platform replacement full Verify41448616746 at2334e266 remains ACTIVE. Four dashboard
shards pass; backend/downstream pending. Source here stays unchanged for prepared
rollout guards. Prior failure preserved. Package/image/checkpoint/clone/deploy/grants
still unexecuted; HRIS103 and isolated Matrix unchanged, push/SMTP off. Reviewed
verify-pilot-access.py is staged in .work/chat-private-rollout-2026-11-02 for after
grants: actual native/MFA contact/device/directory GETs, no provider enrollment or
messages, revoke only its owned operator session. Scripts/config secrets stay private.

Mobile now has FOUR uncommitted Android proof inputs in .work/native-priority-2026-11-02.
Check1 fails33.742s on one function-length rule; extracted phase ownership fixes it.
Check2 fails121.592s because CompanyRecoveryProofOwner still used the pre-priority
worker constructor. Its isolated company-sweep fixture now supplies an explicit empty
priority lane; actual priority uses the new real-container/native helper. Check3 FULL
is active on108 frozen inputs. One local heavy job only. Platform apply-platform.py
and its four intended test/driver changes are only staged under matching .work; apply
after rollout source guards finish. Continue actual SDK proof and notifications/replies,
then remaining54 open groups. Do not end after this metadata push or installation.

## Full CI outage fixture corrected — 2 November 2026

Replacement Verify [41448616746](https://github.com/fajarxfce/hris-platform/actions/runs/41448616746)
is ACTIVE at exact2334e26639eec8204c5e4d481f770a784ea766fb. Do not cancel/duplicate.
Prepared package/image/checkpoint/deploy guards now target this new run/source; the
old failure remains archived. Dashboard still has its original2124c6fc build record
and its source tree is verified equal. The pending deploy script restarts captured
old services on any pre-migration failure; no automatic post-migration rollback.


Full Verify41442782804 at2124c6fc FAILED: seven backend/dashboard jobs pass, backend1
fails one worker case, downstream integration/recovery skipped. The5.803s provider-
outage case sees collaboration_provider_unavailable while paused, then
collaboration_provider_rejected and QUEUED on the first resumed lease. Its assertion
expected immediate SUCCEEDED. Underlying HTTP status/cause is not established.
[Evidence](measurements/chat-provider-retry-2026-11-02.json) retains failed HTML/log
hashes, original source and all jobs; do not call this full acceptance or reuse its
missing packages as a release.

Only CollaborationProvisioningWorkerTest changes. After the paused first attempt,
the fixture gives at most three actual resumed leases (2–4), advancing only this
fictional job's available_at. Each deferral must retain PROVISIONING/no credential
and one of the existing transient provider codes; terminal/unknown failures fail.
Final stable identity/activation checks remain. No production timeout, backoff,
retry cap or source changed. Complete local :apps:worker:test + format/architecture
passes298.222s on the one unchanged input:117 reports/24 suites,116 active, zero
failures/errors and the one existing opt-in elapsed-retention skip.

Push this atomic correction and dispatch one new full Verify at that exact head.
The prepared private rollout scripts under .work/chat-private-rollout-2026-11-02
have been retargeted to replacement41448616746/source2334e266; they still require
all ten jobs to finish successfully. Preserve original failure records. Runtime
application trees are unchanged. Dashboard staged assets remain source2124c6fc;
verify their apps/dashboard tree matches the new CI instead of pretending rebuilt.
Fresh HRIS checkpoint/clone/deploy/grants and phone install remain unexecuted.

Mobile7e3ca0c2 records the clean cc4c11f1 release16 candidate; phone still15. Apple
41446374520 at exactcc4c11f1 is ACTIVE in full Xcode after simulator tests; do not
cancel/duplicate. Mobile currently has two UNVALIDATED Android backup-policy inputs
(manifest + res/xml/data_extraction_rules.xml) and a prepared root/resources/lint
check in .work/android-data-extraction-2026-11-02. Run that next after this worker
check; port the generic rule to Starter only after Mobile validation. Existing
release16 artifact stays intact; a later committed policy build needs17+ if chosen
for phone delivery. Current HRIS schema103 and isolated Matrix stay healthy;
chat push off, all54 groups open. Continue immediately after push/dispatch.

## Pre-enrollment provider recovery and staged release — 2 November 2026

[Evidence](measurements/chat-private-provider-recovery-2026-11-02.json) retains a
cold Matrix checkpoint and two restores. Initial17.085s clone still depended on active
private init SQL; corrected archived-key role initialization passes10.473s against the
same immutable checkpoint. All source/key archive hashes verify; zero users,176 tables,
empty media, restored signing key, no ports, restricted database role and complete
owned cleanup. Source provider is healthy; no new identities/messages. Operator paths,
limitations and concrete pending scripts are in [the plan](collaboration/private-pilot-rollout.md).

Dashboard2124c6fc builds3.729s into immutable collaboration-2124c6fc (220 files,
3,939,446 bytes); include it with API/worker because COLLABORATION extends the API's
module vocabulary. It is not active. Mobile release16 atcc4c11f1 is built/lint-passed
464.885s and verified:87,494,648-byte arm64 APK291fbe05606191876cec1dae8eb02c0b1872210a338d92e8b3a4f125f820c07c,
16KiB alignment, same signer/Firebase/private API, fixture URLs absent. Phone preflight
confirms current15 and same certificate; installation has not started. Prior notice,
explicit10.77.77.3:5555 --install-only and no UI driving remain required.

Apple41441078558 atd93e8cd5 now COMPLETE/audited112 core cases/30 suites,96 combined
source hashes, Swift11s, simulator compile672s/tests150s, full Xcode1320s. New
Apple41446374520 is ACTIVE at EXACT cc4c11f1b538d48680ee892f6709925bbe5e18ec. Do not
cancel/duplicate. Full Platform Verify41442782804 at2124c6fc still ACTIVE: all four
dashboard and three backend shards pass; backend1/downstream recovery/integration
remain. No HRIS checkpoint/migration/deployment or grant yet. Inspect current run,
then stage accepted JARs/images, fresh checkpoint, clone upgrade and explicit rollout.
Current private HRIS schema103/API-worker2c2a59e3/dashboard1e0522c0 and phone15 stay.

One operator read-only inspection logged in with retained bootstrap MFA and revoked
its own temporary native session afterward. Its first summary used a wrong response
field membershipActive; actual active field corrected, saved read results intact.
No permission, company policy, Google binding or employee identity changed. Only two
memberships exist (employee/operator), so add chat to those existing memberships via
reviewed APIs; do not create a fake roster or send messages. Notification privacy/
read/mute/actions/inline reply and all54 groups remain open. Continue after push.

## Isolated provider running; compatible rollout pending — 2 November 2026

[Provider evidence](measurements/chat-private-provider-2026-11-02.json) and
[operator plan](collaboration/private-pilot-rollout.md) record private Matrix/PostgreSQL
startup14.993s and restart/verification11.274s. No host ports; internal network only;
UTF8/C and restricted provider role, stable matrix.hris.internal, protected persisted
signing/JWT/config files. No identities/messages created. Existing HRIS containers and
active dev.env unchanged; API healthy. API/worker2c2a59e3/schema103 are not attached yet.
Private staging ~/.config/hris/matrix-pilot contains rendered/compose-command JSON and
protected original/runtime files; do not regenerate. Matrix bind data and named DB
volume must be retained. HRIS recovery_set.py does not yet include provider backup.

Full Verify41442782804 at2124c6fc still active: four dashboard shards pass, backend and
downstream integration/recovery pending. Apple41441078558 atd93e8cd5 still building full
Xcode. Do not cancel or duplicate either run. Current Mobile cc4c11f1 release16 build
is active in .work/release-phone-2026-11-02-build16/build-arm64.py; inspect result before
another heavy task. No phone change. Finish full acceptance and APK verification, then
fresh coordinated checkpoint plus isolated103→116 upgrade before actual HRIS rollout.
Notify before explicitserial install-only; no phone UI drive. Continue notifications/
actions/inline replies and all54 open groups after usable foreground pilot.

## Priority recovery and private pilot preparation — 2 November 2026

Mobile cc4c11f1b538d48680ee892f6709925bbe5e18ec is PUSHED: one encrypted push hint gets a20s admission/exact-room
original turn, followed by normal fair company recovery. Failed/denied/expired work
retains both hint and global wake. An expired-credential regression reproduced early
completion and is corrected. Root/Android/affected browser checks pass344.646s on16
unchanged inputs:3,262 desktop485 suites,222 host36,981 browser101,10 build-logic2;
zero failures/errors/skips. Its handoff/measurement retain all attempts and source hashes.
This follows pushed room lookup71c7f80c and bounded encrypted queue d93e8cd5.

Apple41441078558 at exact d93e8cd5 is ACTIVE: Swift extraction11s, simulator compile672s
and tests150s pass; full Xcode pending. Do not cancel or dispatch another Apple run.
It excludes the newest priority-processing increment. Prior receiver dd1a449b Apple
41436278627 is audited112 core cases/30 suites plus ten Swift cases and full Xcode.

Platform Collaboration control41437984098 at2124c6fc is completely passed. Downloaded
reports:658 cases/103 suites, six opt-in native/elapsed skips,652 active, no failure/error.
Both authority/storage and company-settings jobs passed. [Evidence](measurements/chat-private-pilot-preparation-2026-11-02.json)
archives report hashes. Full ten-job Verify41442782804 at that exact source is ACTIVE;
audit final jobs/artifacts, do not cancel or treat current progress as acceptance.

[Prepared private rollout](collaboration/private-pilot-rollout.md) records persistent
internal Synapse/PostgreSQL, protected staged config, fresh coordinated checkpoint,
isolated103→116 upgrade, compatible exact-JAR images and identity/document preservation.
No private configuration/service/phone changes yet. Supplementary .work/chat-private-
rollout-2026-11-02 holds initial plan/control artifact audit. Historical final-development-
rollout scripts hardcode old CI/source/schema; review/adapt before use, never rerun verbatim.
Keep provider callbacks/chat FCM off until read/mute/privacy and durable notification
projection/actions/inline replies exist. Native SDK priority proof remains separate.

Private API-worker2c2a59e3/schema103, dashboard1e0522c0 and installed phone15 unchanged.
Next release16+ needs prior notice, explicit10.77.77.3:5555 --install-only, no phone UI.
No Starter port, owned emulator, active goal or subagent. All54 PRD groups remain open;
continue immediately after this documentation commit.

## Native room lookup and push receiver continuation — 2 November 2026

[Room lookup](collaboration/room-lookup.md) / [evidence](measurements/chat-room-lookup-2026-11-02.json):
GET direct-conversations/summary-by-room?roomId= resolves one immutable DIRECT binding
using current company/participant/configured-provider scope, then returns the existing
owned summary. Printable ASCII2..255; encode as a query value. Native transport,
client/module policy and session/MFA/account/company admission apply before AND after
acquisition. Returned ownership/summary identity is checked; no directory scan,
provider call, new room, message or migration. Shared read DI adds the existing
room-scope repository, so API and worker context checks are included.

Check3 passes337.786s on5 unchanged inputs:55 domain/13 suites,147 data/1,2 HTTP/1,
116 active worker/24, zero failures/errors. The unrelated opt-in elapsed export-
retention case stays skipped (worker report117). Format/architecture pass. Check1
failed test compilation25.0s because new outer methods referenced private fixture
DSL; test-owned roomScopes factory fixes it. Check2 passes domain/data and1/2 HTTP
in202.139s; other case was rejected during fixture room publication because its device
was still PROVISIONING. Activate the fictional registered fixture device before
publishing; database guard unchanged. No production code changed after check1.

Mobile dd1a449b9a00a7323d453a48584b179c8e007416 is PUSHED: shared Android/iOS receive
routing commits a fresh generation before fixed recovery scheduling, separate from
Inbox. Local account/native-family/audience ownership ignores credential age; no
workspace/SDK/content I/O at receipt. Root/browser/Android acceptance is recorded in
its chat-push-receive measurement (3,195 desktop,217 host,602 domain browser,26 full
app browser,10 build-logic). The first app-browser filter selected no tests; corrected
unfiltered26/7 suites includes six receiver cases. All executed final cases pass.

Apple41433354322 at8972dc96 failed before any runner/test: hosted macOS capacity.
Replacement41436278627 at EXACT dd1a449b is now active. Ten Swift envelope checks
passed8s; core simulator checks/full Xcode remain pending. Do not cancel or dispatch
another same-branch/scope run while active. Earlier cacae157 Apple audit is retained.

Mobile room lookup is IN PROGRESS on15 frozen Kotlin inputs, with its first focused
check running from .work/chat-room-lookup-2026-11-02/check.py. It adds byRoom to existing
RefreshCollaborationList, raw API/adapter mapping, denial/cancellation/expiry tests and
real app DI/SQLite no-selection/no-SDK reopen acceptance. Do not claim accepted before
inspecting that check, then root/affected browser/Android checks. No local Platform
check remains running once this increment is pushed. Continue that work immediately.

Next: bounded encrypted push-priority hints and fresh hinted-company/device admission,
exact admitted room binding before SDK access, fair all-company recovery retained,
notification read/mute/privacy, durable action deduplication and encrypted replies.
Mobile .work has detailed supplementary notes. Compatible private Matrix/API/worker
rollout and release16+ can expose validated chat progress while chat push remains off
until usable. Preserve identity/Google bindings and data; notice before phone install,
explicit serial --install-only, no UI drive. Current private schema103/API-worker
2c2a59e3, branch116 and phone15 unchanged. All54 groups remain open; no Starter port,
owned emulator, goal or subagent. Do not stop at this checkpoint.


## Android incoming gaps beyond the SDK window validated — 2 November 2026

[Evidence](measurements/chat-incoming-history-waves-2026-11-02.json) verifies three
actual Android/HRIS/PostgreSQL/Synapse variants: QUEUED, FROZEN_DRAFT and CANCELLED_DRAFT.
After receiver SDK closure,175 encrypted peer messages are seeded. Fresh native
admission drives incoming with no outbox or selected workspace. Each retains176
originals, exhausts186 provider references in11 incoming turns and survives11 app
container reopens. Independent foreground Browse takes11 pages with10 state references
remaining. Every body/identity is checked locally; the server checks ciphertext and
exactly176 encrypted sends, including only one outgoing worker send.

Final native runs2+3 pass1/2 cases in394.103s/671.255s on identical APK cb5830040a023fbf4a2ab040800f120521b0286da1d4949f3448966d7b525e50
and unchanged52 Mobile/7 Platform inputs; all cleanup succeeds. Mobile full7 passes
3,169 desktop,217 Android host,908 browser,6 proof host and10 build-logic cases in
428.941s. Later proof-only diagnostic/deadline changes pass APK/host/static8 in96.152s.

Initial native1 retains3/5 failures716.614s: existing SDK death and OS scheduling
regressions pass; two expanded worker fixtures hit210s, and an empty AVD reply causes
IndexError before another installs. Fixture phase/turn counts now identify progress;
complete successful fixtures take245.182–260.457s. Worker-resume fixture allows420s
inside480s Python/10min JUnit; production deadlines are unchanged. Empty AVD responses
now fail before artifact/device mutation.84 driver tests pass0.363s, including bounded
private-field-free diagnostics. First owned emulator's unexpected exit has no established
cause; final owned emulators are stopped. The historical seed provider opts into a
bounded256-message burst; production rate settings do not change. Send acceptance has
an independent bounded counter, so the512-row diagnostic trace cannot truncate proof.

No production backend capability changes in this increment. Incoming turns are explicit;
this is not live FCM/Doze/terminated-app incoming acceptance, OS-scheduled incoming,
foreground-shared focus exhaustion, notification actions/replies, Apple, or load proof.
Phone15/private schema103/API-worker2c2a59e3 remain unchanged by this work; branch116
and disabled chat push remain. All54 PRD groups stay open. Continue Mobile receive/
privacy/actions/encrypted replies, then compatible private rollout and release16+.

Mobile's same-date handoff/measurement has exact source/report hashes and retained
failures. Its current Android receiver still routes only inbox pushes; iOS likewise.
Use a fresh domain recovery-wake generation before fixed scheduling, and treat packet
room/company as hints only. .work receiver candidates are not applied implementation.
No active emulator, goal or subagents remain; continue after publishing this increment.

## Provider-bounded incoming waves validated — 2 November 2026

[Evidence](measurements/chat-incoming-history-boundary-2026-11-02.json) proves existing
Matrix `from`/`to` support against actual isolated Synapse1.164.1, preserving all original
history/context/direction assertions. Starting after25 events,47 delayed events page
in batches of7 while5 additional events arrive after the first page. Adapter recreation
between pages preserves the saved stop boundary; the current wave returns exactly47,
next wave exactly5, and caught-up wave is empty. One case passes11.167s;
Gradle37s /38.171s plus format/architecture, unchanged source. Four
supporting source hashes are retained. These are synthetic encrypted metadata, not
SDK decryption, OS wake or Mobile worker acceptance. No production source changed.

Initial fixture burst128 violated its existing1..100 constructor guard (15.346s),
before provider resources/requests. Corrected to100 for77 total sends. The guard and
production settings remain. Both attempts and XML hashes are retained.

Mobile [cacae157](https://github.com/fajarxfce/hris-mobile/commit/cacae157) is pushed:
Browse/Incoming encrypted partitions/AAD/indexes are independent, legacy Browse bytes
still read, and the shared key guard reuses a concurrently published credential.
A deterministic old-guard failure is preserved; full corrected checks pass3,144
desktop,216 host,886 affected browser and10 build-logic in336.845s. No incoming catchup
caller yet. Apple41414832120 at3062bff2 is complete/audited112 core cases/30 suites,
wake0.124s/Xcode1323s,12 source hashes. New Apple41421351125 is active at exact
cacae157a83c0653428156fc47e53e97bbe1db9d; do not cancel with another dispatch.

**Continue now:** carry optional untilPosition through Mobile API/runtime/page types;
atomically retain incoming first-page start, previous completed stop, continuation and
fair pending-reference position across wakes. Preserve unfinished scans and Browse;
original/tombstone reconciliation remains separate from metadata coverage. Prove an
actual SDK gap beyond128 before FCM/privacy/actions/replies and compatible backend
rollout. All54 PRD groups open, private schema103/API-worker2c2a59e3/branch116/push disabled,
phone15 and Starter5679c51 unchanged. No emulator/phone operation by this increment.

## Actual incoming provider recovery validated — 2 November 2026

Mobile [ee888802](https://github.com/fajarxfce/hris-mobile/commit/ee888802) is pushed.
[Shared evidence](measurements/chat-incoming-provider-2026-11-02.json) binds five
actual Android/HRIS/PostgreSQL/Synapse scenarios: 384.631s, zero failures/errors/skips,
with Platform format/architecture and 81 Python driver cases (0.167s). In each of
QUEUED, FROZEN_DRAFT and CANCELLED_DRAFT the receiver SDK closes before 32 encrypted
peer events are seeded. Actual incoming recovery then uses a fresh directory, native
admission and summary page, with empty outbox and selection reads explicitly rejected.
Each takes one pass and retains 12 originals, including the old outgoing original,
after reopening storage; runtime cleanup and subsequent foreground paging pass.
This is direct fixture invocation with the Android process/peer SDK alive, not incoming
FCM, Doze, process-death, complete long-offline or all-company provider acceptance.

The initial native run failed four of five cases in 356.106s. A raw fixture join saw
503 provider_busy; Mobile adds at most three idempotent M_UNKNOWN attempts. Three
incoming cases reached foreground but failed Unavailable/Unclassified. The fixture
now republishes foreground retention after reopening; old diagnostics cannot establish
an exact code or incoming counts. A new bounded incoming checkpoint survives later
foreground failures. Production M_UNKNOWN mapping is now Service/retryable, with a
shared regression case. Final trace recovered keys/query and keys/upload 503s; do
not claim the final run exercised a join 503. Both attempts and hashes are preserved.

Mobile final root/proof/Android release/data-browser checks pass in 301.21s:
3,124 desktop, 216 Android host, 6 proof host, 299 data browser and 10 build-logic.
Seven Mobile and four Platform inputs plus APK 5f17598f are frozen through native2.
All driver cleanup succeeds; owned emulator 5562/PID99409 stopped in 1.003s.
Unrelated 5560 and phone 15 are untouched. Apple 41414832120 remains active at
3062bff25c678639204bcf4a5e2e95be85c155d4; simulator tests passed, Xcode building.
Do not cancel it with another dispatch; audit artifacts on completion. It excludes
later proof/mapper changes. No generic Starter port is due.

**Continue immediately:** independent durable incoming history traversal/frontier and
pending-reference progress without resetting foreground Older. Then FCM privacy,
actions/encrypted replies and compatible private backend rollout. Latest-window
acquisition alone is not long-offline catchup. All 54 PRD groups remain open.
Private schema103/API-worker2c2a59e3, branch116 and disabled chat push remain;
next validated employee release is16+ with prior notification and install-only.

## Incoming recovery implemented; native driver selects owned emulator — 2 November 2026

Mobile [3062bff2](https://github.com/fajarxfce/hris-mobile/commit/3062bff2) adds incoming
acquisition independent of outgoing work, with one company per turn and separate15s
incoming/outgoing budgets. Actual app/DI/domain/SQLite tests cover no-outbox restart,
room fairness, original deadlines, late account/denial, cancellation and cleanup.
Full checks pass3,123 desktop,215 Android host,865 browser,10 build-logic (188.013s).
All four actual WorkManager/Room/Keystore scheduling scenarios pass46.417s, including
21 healthy company callbacks with no Retry. SDK/company callbacks in that scheduler
proof are fictional; real incoming provider acceptance remains next. Failed test
assertions are retained in Mobile evidence. Room foundations af5cb4dc are committed.

Apple41409790492 at5a39b53f is complete/audited:112 core cases/30 suites, wake0.083s,
full Xcode1269s,10 verified source inputs. It excludes later room/incoming changes;
Mobile is dispatching its newer exact implementation head. Phone15 unchanged.

[Driver change](measurements/native-proof-emulator-selection-2026-11-02.json) accepts
HRIS_COLLAB_NATIVE_SERIAL=emulator-5562 across SDK/worker/scheduled interop. CLI provider
proof also allows5562. Legacy5560 remains supported, but is currently an unrelated
CodePush AVD: do not operate it. Both allowed serials still require the exact
hris-mobile-dev AVD and separate debug proof package; unowned data/mappings, phone
endpoints and unclaimed connectivity mutation remain rejected. Three new cases;
all79 collaboration driver tests pass0.179s. Actual alternate-serial SDK proof follows.

**Continue now:** extend existing actual worker/provider fixture to close sender
runtime before peer seeding, acquire incoming with fresh real directory/admission
and unavailable workspace selection, verify owned originals survive reopen, then
retain foreground older-history proof. Next independent recent catchup, FCM privacy/
actions/replies and compatible backend rollout. Latest bounded window is not long-
offline recovery. All54 PRD groups remain open. Private schema103/API-worker2c2a59e3,
branch116, disabled chat push and Starter5679c51 remain. Owned5562/PID65117 is stopped.
Continue after each commit; no employee install or live change by these increments.

## Healthy recovery batches validated; incoming room progress underway — 2 November 2026

Mobile [5a39b53f](https://github.com/fajarxfce/hris-mobile/commit/5a39b53f) is pushed.
Successful bounded company batches now persist continuation separately from failures,
so normal remaining work schedules the same wake without failure backoff. Legacy
schema1 progress reads under schema2 without losing its original wake/seek/failure.
Full checks pass3,073 desktop,215 host,836 affected browser,10 build-logic with10
unchanged inputs (292s /295.868s). Actual WorkManager/Room/Keystore proof passes all
four scenarios37.916s; three healthy batches cover seven companies/21 callbacks in
1.593s with zero Retry results. Interrupted work still retries after later companies.
Directory/admission/company callbacks are synthetic; SDK/FCM acceptance remains open.
Failed raw-generation and two-clock admission fixtures are preserved in evidence.
Owned emulator5562/PID15507 and proof process are stopped; phone15 unchanged.

Mobile Apple41405822268 at338fea17 is complete/audited:112 cases/30 suites,
6 wake tests0.122s, full Xcode1303s and14 verified source hashes. It excludes the
later continuation change. New [Apple41409790492](https://github.com/fajarxfce/hris-mobile/actions/runs/41409790492)
is active at5a39b53f0ece41d314738090e330bd7004e528db; do not cancel with another dispatch.

Mobile currently has13 UNCOMMITTED Kotlin inputs under focused checks: account/company-
owned room seek/in-flight/retry/continuation progress, encrypted CAS repository,
12 domain/9 shared SQLite-IndexedDB cases and actual DI. No incoming app caller yet.
Complete validation, captured history-reference scope, then independent incoming
acquisition and actual SDK/provider/FCM/privacy/reply integration. Foreground older-
history position must not be reset on every background wake; long offline gaps need
independent catchup progress. All54 PRD groups remain open. No Starter port is due
for this HRIS policy. No Platform deployment: private schema103/API-worker2c2a59e3
remain; branch schema116 is not yet live and chat push stays disabled. Continue.

## Android company recovery proved; healthy backlog correction underway — 2 November 2026

Mobile [20d1592b](https://github.com/fajarxfce/hris-mobile/commit/20d1592b) records
actual WorkManager/company consumer/Room/Keystore execution against consumer338fea17.
Three scenarios pass35.839s:40 rejected enqueues preserve newer wakes, max2 OS jobs
serialize one company processor, old ACK cannot erase a new wake, and interrupted
attempts survive process death while later companies advance. Proof process, owned
emulator5562 and all jobs/resources are closed; phone15 and unrelated5560 untouched.
Callbacks for directory/admission/company work are fictional: no SDK/FCM acceptance.
The measurement preserves failed fixture attempts and exact17 source/APK hashes.

Mobile currently has9 UNCOMMITTED Kotlin changes correcting healthy company backlog:
SyncResult.Retry(null) means a successful bounded batch, so it must schedule a
continuation without treating the sweep as failed. Durable progress now separates
continuation from failures/uncertainty, reads schema1 and writes schema2. Tests cover
multiple batches, restart, failure priority, scheduler failure and storage upgrade.
Focused1 passed547 domain; two of311 data cases failed in their raw fixture because
it attempted to reuse a generation. Fixture corrected to fresh authenticated IDs;
focused2 is running. Native backlog scenario is added but not run. Preserve attempts,
finish full checks and actual native execution before committing/accepting changes.
Then continue incoming room discovery/acquisition with durable fair progress, FCM,
notification privacy/actions and encrypted inline replies. All54 PRD groups open.

Mobile [Apple41405822268](https://github.com/fajarxfce/hris-mobile/actions/runs/41405822268)
at338fea17b5738698f45c824cd1d6a06142050946 is active: simulator tests succeeded,
Xcode app building. Do not cancel it with another same-branch dispatch. Audit XML,
exact source and full Xcode completion; it excludes the new backlog correction.
Starter5679c51 generic port/Apple proof complete. No new generic port is due here.
Private Platform schema103/API-worker2c2a59e3 and disabled chat push remain unchanged.
Continue after every commit/push; Mobile's current handoff supersedes older entries.

## Company progress proved; all-company worker in development — 2 November 2026

Mobile [d262799b](https://github.com/fajarxfce/hris-mobile/commit/d262799b) is pushed:
account-owned encrypted company seek, unfinished attempt and sticky retry state
survive restart and reauthentication. New wakes cannot reset a finite sweep;
clean local completion precedes exact original-wake acknowledgment. Twelve new
domain/eight storage cases cover fairness, cancellation, concurrent writes, lost
responses, account replacement, tampering and key loss. Focused checks pass542
domain/308 data/two DI plus Android/static in152s /155.704s. Full root/browser/
app-Wasm passes3,044 desktop,215 host,828 affected browser and10 build-logic in
296s /299.533s on13 unchanged inputs. No consumer was wired by that commit.

Mobile now has an UNCOMMITTED next increment under focused validation: registered
all-company worker, one processor/one bounded waiter, processed-parent cancellation,
periodic wake creation, preserved-generation pagination and captured delivery. Its
new tests cover simultaneous calls, late results, scheduling/ACK gaps, fair retries
and actual application DI. Current binding uses the existing outgoing delivery
worker; incoming discovery/decryption and fair room progress still need their own
path before FCM activation. Continue implementation and native provider proof;
do not infer acceptance from this in-progress source.

Starter [5679c51](https://github.com/fajarxfce/compose-fluent-starter/commit/5679c51)
records audited Apple41400133440 at exact136d2e5a:92 simulator cases/26 suites,
six AppleWorkWakeStoreTest cases0.178s, zero failures/errors/skips, full Xcode586s.
The generic port is complete; no additional dispatch is pending. HRIS policy stays
in Mobile. Mobile/Starter measurements bind exact source and report hashes.

No server rollout: private schema103/API-worker2c2a59e3 remain; push disabled and
all54 PRD groups open. Phone remains15, no new installation. Owned5562 remains
stopped;5560 is unrelated CodePush. After builds finished, the owned temporary8GiB
swap was safely disabled (47.997s) and removed with14.46GB available memory; the
original /swap/swapfile remains enabled. Continue after every push.

## Durable recovery and bounded Android scheduling proved — 2 November 2026

Mobile [380cc654](https://github.com/fajarxfce/hris-mobile/commit/380cc654) persists
fixed-key recovery wakes before enqueue, retains them through failures/cancellation,
and fences acknowledgment against newer generations. Ten domain/six data cases,
actual DI/SQLite and full checks pass (3,016 desktop,215 host,808 browser,10 build-logic).
[e00ab4ee](https://github.com/fajarxfce/hris-mobile/commit/e00ab4ee) adds opt-in two-slot
Android scheduling, bounded32 callers/keys and eight common regressions. Full root
passes3,024 desktop/215 host/13 worker-browser/10 build-logic. Actual emulator proof
passes40 competing requests, two simultaneous tasks, backoff reuse, failed/cancelled
slot recovery and process-death repair in22.458s. Five final inputs and APK remain
unchanged throughout. There is no employee task opt-in or FCM acceptance yet.

Mobile raw database Apple run41396150119 at be8ebc7b is completed/audited:112 cases
in30 suites, six real SQLite wake cases,zero failures/errors/skips and complete
Xcode app. Starter136d2e5 ports the three generic scheduler files and that Apple
fixture with independent148s /152.12s root/browser/Android/lint/Web proof (356 desktop,
13 worker-browser,six build-logic). Starter Apple run41400133440 is active at exact
136d2e5a607fb20b6f5ccd07bd88ed14b0a6b668; audit XML/Xcode before acceptance. Do not
cancel it with another same-branch dispatch. Mobile/Starter handoffs and measurements
contain all source/artifact hashes and next steps.

Continue HRIS account-owned company recovery progress and all-company consumer,
using current native directory/admission and incoming sync independent of outgoing
work. Preserve sweep fairness and crash gaps without false acknowledgment; incoming
wakes must not reset an unfinished sweep. Then receive/decrypt, notifications and
encrypted inline replies. WorkManager backoff reuse is not proof of immediate FCM
latency; integrate and measure it separately. All54 PRD groups remain open.

Phone stays release15 from30d7faab; no live deployment, private schema103/API-worker
2c2a59e3 unchanged, branch push still disabled. Emulator5562/PID4105492 was stopped;
5560 now belongs to unrelated CodePush AVDcodepush-ci08/PID4082879 and was preserved.
Recheck ownership before using any emulator. Continue after each commit/push.

## Durable wake foundation proved in Mobile and Starter — 2 November 2026

Mobile [be8ebc7b](https://github.com/fajarxfce/hris-mobile/commit/be8ebc7b) adds
bounded application-global scheduling generations with transactional capacity,
replay and exact-generation acknowledgment. Room11→12 and IndexedDB10→11 retain
prior data. The first SQLite run found already-cancelled writes committing; the
fix passes actual SQLite/browser regressions and full checks (3,000 desktop,
209 Android host,42 database-browser,10 build-logic). Details and failed-run
history live in Mobile's work-wake-storage measurement.

Starter [dab675a](https://github.com/fajarxfce/compose-fluent-starter/commit/dab675a)
ports 15 identical shared source/test/schema files after Mobile proof. Independent
root/database-browser/Android/lint/production Web checks pass in301s /305.231s:
348 desktop,42 browser and six build-logic cases, zero failures/errors/skips.
Mobile app policy/configuration is excluded. Its six Apple wrappers await Mobile
hosted run41396150119 at be8ebc7b before port and independent Starter Apple CI.
No result is accepted until actual downloaded XML and Xcode steps are audited.

Continue Mobile fixed-key wake repository/Request/Resume/Read/Complete policies;
12 uncommitted inputs are currently under focused checks with ten domain/six data
regressions and actual application DI/SQLite coverage. Preserve any failures,
then full root/affected browser validation. These are not yet invoked by a
registered all-company worker or FCM receive path. Next: bounded WorkManager
coordination, fair company/room discovery, actual decrypt/notification/reply.

Phone remains verified15 from30d7faab; no server rollout. Private schema103/API-
worker2c2a59e3 remains, branch push foundations stay disabled. All54 PRD groups
remain open. Continue after each commit/push; Mobile handoff has exact next steps.

## Mobile processed-parent publication validated — 2 November 2026

Mobile [1a55c2a5](https://github.com/fajarxfce/hris-mobile/commit/1a55c2a5) exposes
processed retention, ownership and cleanup results from its sole Maintain collector.
Application state explicitly distinguishes pending/observed/stopped, invalidates on
close/completion and rejects late results. Cleanup failure does not erase a processed
parent or claim native store readiness. Legacy denial priority and selected task-key
scheduling remain. Ten new cases pass; full checks pass in298s /301.988s with2,993
desktop,209 Android host,792 browser and10 build-logic cases, zero failures/skips.

Continue Mobile durable collapsed wake storage, fair all-company recovery and actual
receive/notification/reply. Its raw global storage candidate is incomplete/unapplied,
with Room11→12 and IndexedDB10→11 additions; finish concurrency, capacity, cancellation
and real migration/reopen tests first. Preserve one-time APPEND_OR_REPLACE race
protection while bounding scheduling. Duplicate deterministic APPEND IDs are unsafe
per inspected WorkManager2.12 sources; validate the proposed persist-before-pending
query approach on actual WorkManager. Do not use synthetic account/session scope.

Phone remains verified15 from30d7faab. No new JNI/APK/live rollout or Starter port
for this increment. Generic wake foundations return to Starter after Mobile proof.
Backend push stays disabled; all54 PRD groups remain open. Owned emulator5560 is
stopped; preserve unrelated5558/jobs. Temporary swap remains pending safe cleanup.
Continue after each push; Mobile handoff and measurements have exact next steps.

## Mobile release15 delivered with actual Android regression evidence — 2 November 2026

Mobile [30d7faab](https://github.com/fajarxfce/hris-mobile/commit/30d7faab) fixes
initial native-parent retention before workspace observation, retains foreground
through same-parent token rotation and anchors hard expiry across clock rollback.
Its final root/browser checks pass:2,983 desktop,209 Android host,788 browser and10
build-logic cases. [94d62e11](https://github.com/fajarxfce/hris-mobile/commit/94d62e11)
records five actual SDK/HRIS/Synapse regression passes with cleanup in364s /365.046s
against clean Platforma9706280. The same clean Mobile implementation builds signed
devRelease15, passes two12s actual application cold starts, then produces an ARM64
APK with all eight DEX files identical to the startup-tested universal build.

The authorized phone now has release15,87,363,576 bytes, SHA-256
`39a8bb9cfbaf95c394e228d16aebc2085448d8c4c3d2b0f051fadc13eca6a79e`.
Install-only update preserved signer/data/UID/first-install time; full installed-file
hash was computed on the phone. A slow preliminary read-only pull was interrupted
and preserved in the measurement. No phone UI launch or new user acceptance is
claimed. Build/lint pass with zero errors and one existing warning.

Continue Mobile explicit processed-parent publication from its sole Maintain
collector; six files/ten regression cases are prepared but unapplied at this
checkpoint. Distinguish pending/observed/stopped, separate cleanup failure from
parent identity, and suppress publication after close or source termination.
Then implement durable coalesced all-company wakes, fair directory recovery,
receive/decrypt, notification privacy/read/mute/deep links and encrypted replies.
One-time Android scheduling already uses APPEND_OR_REPLACE, not KEEP; preserve
its finishing-worker race fix while bounding new burst scheduling and testing
persist/schedule/ack gaps. Mobile handoff has the candidate and validation details.

No live schema/provider rollout or generic Starter port; private server remains
schema103/API-worker2c2a59e3 and backend collaboration push stays disabled. All54 PRD
groups remain open. Owned emulator5560 is stopped; preserve unrelated5558/jobs.
Owned temporary swap remains active pending safe RAM headroom. Continue after pushes.

## Mobile shared admitted background worker validated — 2 November 2026

Mobile [d8ebe56f](https://github.com/fajarxfce/hris-mobile/commit/d8ebe56f) extends
captured local scope through discovery/cursors, frozen draft queue/confirmation/
review and access observation. Actual encrypted recovery after restart preserves
one original intent without workspace reads. Its root/browser checks pass with
2,960 desktop,209 Android host,781 browser and10 build-logic cases.

[0539e241](https://github.com/fajarxfce/hris-mobile/commit/0539e241) adds an ephemeral
background-admission entry to the existing delivery worker. Exact setup readmission,
captured local recovery and finite native borrowing share the original wall and
monotonic deadline. Local/remote inner room logic remains shared. Independent
credential/runtime/ownership signals cancel pending delivery and dispose observers;
release closes only the borrowed reference. History capacity defers background
work without reclaiming foreground or resending. Simultaneous worker calls return
Retry(busy) without waiting. No serialized grant or message body enters platform jobs.

Sixteen new app cases pass within43 actual collaboration application tests;509
domain cases, actual DI, proof adapter compilation and static checks pass. Final
root and app Wasm compilation pass in327s /330.664s on seven unchanged inputs:
2,976 desktop,209 Android host and10 build-logic cases. The evidence retains the
initial52s run's two assertion corrections (old waiting behavior and an omitted
staging-queue close); no product algorithm changed for those corrections. Simulated
SDK/control-plane fixtures do not constitute new JNI/provider acceptance.

Continue Mobile initial native-parent readiness and monotonic observation across
pending workspace reads, token rotation and clock rollback. Its three-file/seven-case
candidate is currently being checked. Then persist all-company collapsed wake
generations/fair cursors, directory/reconnect/periodic recovery and real receive,
notification privacy/read/mute/deep links and encrypted inline replies. Reuse the
single application collector and explicitly await initial retention publication.
Backend delivery remains disabled; all54 PRD groups remain open. Continue after
pushed checkpoints. Private server remains schema103/API-worker2c2a59e3; no live
rollout, new APK/phone install or generic Starter port. Phone stays verified14.
Owned temporary swap remains active pending safe cleanup; preserve unrelated jobs.

## Mobile captured local recovery and background control validated — 2 November 2026

Mobile [57df24aa](https://github.com/fajarxfce/hris-mobile/commit/57df24aa) separates
captured local account/company ownership from remote admission deadlines. Message
observation, original-receipt recovery/projection/retirement and retained control
reads work without workspace selection, while preserving retained company denials.
[2cc2a21a](https://github.com/fajarxfce/hris-mobile/commit/2cc2a21a) extends typed
background scope through setup and conversation prepare/replay/reconcile. Setup
readmission requires the matching active device/platform and nonregressing version
before gate CAS. Room requests require the exact admitted device.

Historical command failures retain explanatory priority but cannot themselves
confirm a new denial. An explicitly readmitted retry keeps its original operation
ID; newly observed HRIS denial still gates the company under cancellation or failed
cleanup. These two increments add31 domain cases. Latest focused500 domain,
118 presentation and two actual DI cases pass; proof adapter compilation and static
checks pass. Final root/browser checks pass in249s /252.715s:2,949 desktop,
209 Android host,772 browser and10 build-logic cases, zero failures/errors/skips.
See Mobile's contracts and measurement records for exact source hashes and scopes.

Continue captured delivery/draft discovery/cursors/queue/confirmation, then reuse
the existing bounded worker with separate local and remote scopes. An applied
Mobile candidate is being checked; its current Git handoff identifies the scratch
candidate baseline and next checks. Next hold finite background runtime references,
watch interruption/credential failure, and test initial parent readiness without
waiting for workspace selection. Durable all-company wake/reconnect/periodic
recovery, notification privacy/read/mute and inline replies remain required.

No server implementation change, live schema/provider rollout, new JNI acceptance,
APK build/install or generic Starter port here. Phone stays verified14 from8c557716.
Backend delivery remains disabled; all54 PRD groups remain open. Continue after
pushed checkpoints. Owned temporary swap stays active pending safe cleanup;
preserve unrelated jobs and AVD5558.

## Mobile shared background history/outbox scope validated — 2 November 2026

Mobile [1d6f9f08](https://github.com/fajarxfce/hris-mobile/commit/1d6f9f08) adds
Selected/Background native conversation entry and original/provider history.
[9866eb95](https://github.com/fajarxfce/hris-mobile/commit/9866eb950916c940d3c8ef124b46452ebfba9a79)
extends that scope through existing staging/delivery and corrects SDK-token failure
classification. Neither background path reads or changes workspace selection.
Explicit device binding, finite original admission, child cleanup, uncertainty,
receipt retention and existing bounded merge/projection rules remain shared.
SDK-token unauthorized no longer closes all local company history; confirmed HRIS
denials still retain their gate even under cancellation or failed cleanup.

The two increments add25 domain cases. Latest focused469 domain/two actual DI cases,
proof adapter compilation and static checks pass in130s /134.385s. Root and affected
browser checks pass in254s /257.828s:2,918 desktop,209 Android host,741 browser and10
build-logic cases. The records retain an initial history LongMethod failure and an
outbox fixture's incorrect resume-count expectation, plus their corrections. Sent
mappings can participate in a later SDK queue inspection; recovery retains the same
logical intent/transaction and receipt, with no replacement enqueue. No new JNI
run, APK build or server implementation change in these increments.

Continue captured local ownership for observation/receipt recovery/projection and
retirement, then local control/discovery/cursor/drafts, remote room-control scope
and the existing bounded application worker. Keep local fact lifetime separate from
remote grant expiry. Reuse the application runtime observer; test initial parent
readiness while workspace observation is pending and preserve foreground retention
across token rotation. Durable all-company hints/reconnect/periodic recovery,
notifications/privacy/read/mute and inline replies remain next. Delivery stays
disabled and all54 PRD groups remain open; continue after checkpoints.

Phone remains verified release14 from8c557716, predating these later changes. No live
schema/provider rollout or generic Starter port. Owned temporary swap remains active
pending safe cleanup; preserve unrelated CodePush jobs and AVD5558.

## Mobile release 14 installed and verified — 2 November 2026

Mobile [4e4c1bd9](https://github.com/fajarxfce/hris-mobile/commit/4e4c1bd9) records
the notified install-only update on the authorized WireGuard phone. Clean8c557716
source produced the arm64 devRelease14 APK; all eight DEX files match its actual
application startup-tested universal14. Installed bytes were pulled and match
SHA256 `be3d42117c0d52e961f668b37ee05625916ef5bece72d0e067500b14fad2945d`.
The signer, application UID and first-install time are preserved. No app-data
clear, uninstall or UI launch occurred. Phone now has14; older11 notes below are
historical. The user confirmed11 opens, not14.

Continue Mobile typed native/history scope, shared outbox and durable all-company
receive/recovery. Keep backend delivery disabled until actual receive acceptance.
No live server change or generic Starter port. All54 PRD groups remain open.

## Mobile finite background runtime references validated — 2 November 2026

Mobile [8c557716](https://github.com/fajarxfce/hris-mobile/commit/8c5577167f86f47d0eebd03ff04e57ae5351a99e)
is pushed with [contract/evidence](https://github.com/fajarxfce/hris-mobile/blob/8c5577167f86f47d0eebd03ff04e57ae5351a99e/docs/collaboration-background-runtime.md).
It retains native parent separately from selected company, reserves a single SDK/store
before construction and lends finite background references without parent close handles.
Releasing a background reference preserves foreground ownership; pending acquisition,
parent expiry/replacement, failed drain and history-reopen races are covered. Admission
budgets include network/acquisition time using a monotonic deadline.

All five actual SDK/HRIS/Synapse cases are accepted against Platform2308b337
(implementation8b77c4fa) with the same proof APK and unchanged Kotlin sources. The
first run's two interruptions are retained: the kernel killed the owned emulator
for host OOM. The queued/frozen retry passes in169s /169.876s with cleanup. Root,
Android-host, browser and release checks pass; the record also preserves two browser
fixture timeouts and a reduced-heap D8 failure plus their successful follow-ups.
Universal14 passes two12s actual-application cold starts. No server source change.

The immediate Mobile task is a clean-source arm64 release14 build and notified
install-only update to10.77.77.3:5555, with signer/data and installed-byte verification.
Phone still has user-confirmed11 until that evidence is recorded. Then continue typed
native history/background scope and shared outbox/all-company receive recovery.
ApplicationCollaborationLifetime already starts independently of activities; test
initial parent readiness without a selected company before wiring incoming work.
Keep delivery disabled. All54 PRD groups remain open; continue after checkpoints.

Owned emulator5560 is stopped. An8GiB temporary Btrfs swapfile at
`/var/tmp/hris-validation-2026-11-02.swap` is active, not in fstab, to prevent further
host OOM during concurrent unrelated CodePush work. Preserve unrelated jobs/AVD5558;
remove only the owned swapfile once sufficient RAM and the validation workload permit.
No live schema/provider rollout or generic Starter port.

## Mobile recovery client and native publication checkpoint — 2 November 2026

Mobile [5aa6f752](https://github.com/fajarxfce/hris-mobile/commit/5aa6f75260eb429e98cbb34c600bcbec1c7ee57c)
is pushed. Its preceding commits implement the own-parent directory/admission client
and typed Selected/Background conversation reads. Final native runtime publication
now follows admission in Ensure, Install, Restore and history reopening; pending or
failed callers cannot expose a client before transferring ownership. The native
proof adapter is compiled explicitly and includes the earlier pusher API method.

Against this repository's unchanged8b77c4fa source, the same proof APK passes all
five actual SDK/HRIS/Synapse scenarios in369s /369.345s: encrypted SDK recovery,
scheduled ordinary process death, queued intent, frozen-only draft and cancelled
draft with independent delivery. Cleanup passes in all cases. Mobile CI now pins
8b77c4fa; no hosted result is claimed. Mobile root/browser/native-host/build checks
also pass. See its [contract and evidence](https://github.com/fajarxfce/hris-mobile/blob/5aa6f75260eb429e98cbb34c600bcbec1c7ee57c/docs/collaboration-runtime-publication.md).

Continue finite background SDK borrowing alongside foreground retention, parent
replacement/expiry and pending acquisition cleanup. Then extend the existing scope
through history/outbox and implement durable all-company hints/reconnect/periodic
recovery, FCM receive/decrypt/privacy/read/mute and inline replies. Never change
visible workspace selection for a background pass. Keep delivery disabled until
real receive/recovery acceptance; classic SyncV2 cannot use the SDK's sliding-sync
NotificationClient as a drop-in. No server implementation change in this note.

Phone remains user-confirmed release11; prior local universal13 was validation
only. Next useful product update14+, notify first, explicit10.77.77.3:5555
install-only with signer/data preserved. Owned emulator5560 is stopped. No live
provider/schema rollout, generic Starter change or full PRD group acceptance.
Continue after each pushed checkpoint; all54 groups and remaining P0–P8 stay open.


## Native recovery directory and company admission validated — 2 November 2026

[Contract](collaboration/native-recovery.md) / [evidence](measurements/chat-native-recovery-directory-2026-11-02.json):
GET /api/v1/collaboration/native-devices returns bounded own-parent device references
without selecting a company. GET /api/v1/companies/{companyId}/collaboration/native-device/admission
exposes the existing protected admission observation. Metadata remains discoverable
after company access loss/MFA expiry; content admission still requires current
company/member/account/native-parent/device/MFA/client policy. No schema/RLS widening.

55 domain, 144 registration SQL, 15 actual HTTP and seven worker tests pass;
format/architecture, 144s Gradle /145.191s wall, 15 unchanged inputs. Final API/worker
tasks execute; unchanged domain/data tasks reuse their passing reports. Earlier
MFA fixture failures and the actual interceptor correction are retained. A separate
strict HTTP case performs real MFA enrollment/native exchange, expires only its
MFA proof, then proves metadata200/admission403. The exact metadata method uses
PendingMfaAllowed; no company endpoint receives that annotation.

Mobile10dce241 has the validated managed pusher operation, full root/browser/release
checks and two actual-app cold starts. Its typed recovery directory/admission client
is now implemented and undergoing tests. Continue finite foreground/background SDK
ownership, all-company durable hints/reconnect recovery, receive/decrypt/privacy and
inline replies. Do not substitute device discovery or the60-second observation for
content authority. Current SDK NotificationClient requires sliding sync; product
SyncV2 needs an explicitly scoped integration. Keep backend delivery disabled.

Phone release11 is user-confirmed working; next useful product build12+, notify
first and explicit10.77.77.3:5555 install-only, preserving signer/data. Owned emulator
5560 is stopped. No generic Starter change yet, no live provider/schema rollout and
no full PRD acceptance. Continue P0–P8 and proved reusable ports after every push.


## Automatic chat push worker validated — 2 November 2026

[Evidence](measurements/chat-push-worker-2026-11-02.json): the worker now owns four
parallel delivery slots (maximum eight), no waiting task queue, a 250 ms poll and
20-second task deadlines. Timed-out work retains its occupied slot until it exits.
Shutdown interrupts claims and closes both executors. Durable leases, current
prepare/send/ACK guards and finite retries remain in HandleCollaborationPush.

Eleven tests pass: four lifecycle/bounds cases, one case with seven actual
WorkerApplication contexts, and six existing provisioning worker cases. Enabled
context claims the real PostgreSQL queue under the worker role; absent delivery
opt-in and each required capability/global-worker disablement produce no worker.
Every context terminates its owned pools. Format/architecture pass; 119s Gradle /
119.725s wall, six unchanged source/configuration inputs. No FCM send or live
configuration change is claimed by this worker-context proof.

**Continue immediately:** Mobile managed pusher activation, receive/decrypt/reply
and all-owned-company recovery. Existing runtime ownership follows only the selected
workspace, so background recovery must gain explicit bounded account/native-parent
ownership without changing the visible selection or relaxing interactive admission.
Keep HRIS_COLLABORATION_PUSH_DELIVERY_ENABLED false until those paths are validated.
Continue remaining P0–P8 and reusable Starter ports after each pushed checkpoint.
Mobile release 11 is user-confirmed working; next phone update is 12+, notification
first and explicit 10.77.77.3:5555 install-only. Leave unrelated emulator 5554 alone.


## Current chat wake delivery and Android startup fix proved — 2 November 2026

[Delivery evidence](measurements/chat-push-delivery-2026-11-02.json): queued
claim/prepare/send/ACK now uses complete current authority, owned room, token version
and exact captured lease. External send stays outside transactions.20-second dispatch
is separate from24h storage; fixed opaque payload/generic fallback and stable wake
identity. Callback during I/O survives old ACK; newer visible identity cannot leak
into an older lease. Late UNREGISTERED cannot retire a replacement token. Current
retirement/ACK roll back together on failure, late time or interruption. Retry is
bounded to8 attempts; new callbacks recover after exhaustion.

55 domain +138 full registration SQL +42 actual API +6 actual worker cases pass,
format/architecture,393s /393.482s, seven unchanged inputs. First92s run had one
fixture failure: arbitrary available_at mutation hit the required transition trigger.
Second66s all18 SQL pass, then KDoc formatting failure. Final first retry really
waits5s; later waits are compressed via actual fenced defer while requested delays
and finite attempts are checked. Do not claim615s elapsed backoff or live FCM delivery.

**Continue immediately:** add owned worker polling/task/deadline adapter, local4/max8,
no waiting task queue,60s leases,20s delivery budget,250ms poll, global16 pilot cap.
Use an independent default-disabled HRIS_COLLABORATION_PUSH_DELIVERY_ENABLED flag
and require FCM plus collaboration/Matrix/push. Reuse DeadlineTask cancellation;
prove shutdown, occupied-slot behavior after timeout, and actual enabled/disabled
worker contexts. Then Mobile receive/decrypt/reply, all-company recovery and ports.
Current source has no adapter, live rollout or full PRD acceptance. All54 remain open.

User reported release10 force-close during this work. Mobile56a46938 fixes actual
Android host composition passing an API path to root-only Matrix configuration;
API audience and encrypted account ownership are preserved. Full root check plus
actual release-app two cold starts pass. Release11 was installed and pulled-byte
verified with preserved signer/data; user confirms “Sudah bisa dibuka”. Mobile
50a83952 records acceptance. Next product phone update12+, notify first, explicit
10.77.77.3:5555 install-only. Owned emulator5560 is stopped; unrelated emulator5554
was observed and must be left untouched. Continue full P0–P8 after every checkpoint.


## Actual private TLS callback and provider retry proved — 1 November 2026

[Evidence](measurements/chat-push-provider-callback-2026-11-01.json) /
[deployment configuration](../deploy/synapse/README.md): pinned Synapse1.164.1 and
Nginx1.30.6 load the exact private TLS listener/public-denial include. Public403,
private200,8193-byte413 and untrusted-certificate rejection pass. An ephemeral
owned CA is trusted normally by Synapse through SSL_CERT_FILE; no TLS bypass.
Actual proxy outage produces Synapse failing_since; recovery retries the exact
synthetic encrypted event through API/RLS into the durable queue and clears that
failure. Native-parent revocation then makes Synapse remove the pusher, retaining
the previous queued event. Test1/39.909s suite/17.622s method; format/architecture
pass,220s Gradle /221.378s wall, six unchanged inputs. Dependency catalog update
rebuilt modules; first15s compiler failure required test-only bcpkix at existing1.86.
Room publication is fixture SQL; native decryption and worker provisioning are
separate existing proofs. All test containers are reaped; no live deployment.

**Continue immediately:** implement current-target-fenced queue/FCM worker using
HandleCollaborationPush's existing guarded observation, then Mobile receive/decrypt/
reply, all-company reconnect/periodic/full recovery, and proved Starter ports.
`.work/chat-worker-next.txt` captures concrete lease, target/registration, lifecycle,
retry and payload considerations. Core retention/dispatch, current append and HTTP
are implemented; do not recreate them. Sender must have its own disabled-by-default
opt-in and require FCM configuration. Current16 global leases are pilot bounds.
Phone remains release10/c037; next validated Mobile product11+, notify first and
explicit10.77.77.3:5555 install-only. Continue full P0–P8, all54 groups unaccepted.

## Private callback HTTP and runtime graph proved — 1 November 2026

[Evidence](measurements/chat-push-private-ingress-2026-11-01.json): default-disabled
`POST /_matrix/push/v1/notify` uses numeric socket-peer/CIDR admission, strict8KiB
body/32-key decoding, four body readers and four cancellable dispatch slots with
no waiting queue/10s budget. Source denial precedes body acquisition; forwarded
headers never authorize. Current queue/handler DI now composes in API and worker.

14 delivery +42 actual API +6 actual worker cases, format/architecture pass in116s /
117.140s wall, eleven unchanged inputs. Real127.0.0.2 spoofing is denied before any
body; real stalled body returns408 at about5s and the next request works. Preserve
first12s vararg-lambda compiler fix and second76s actual timeout-response failure:
Tomcat ERROR dispatch changed408 to401 via native auth. The callback-only ERROR
filter now uses server attributes/source checks and fixed output, leaving the
ordinary native error chain unchanged. Existing guarded admission commit4fe92b35
and core dispatch/retention08fec5ae are pushed. No live migration or app change.

**Continue immediately:** private TLS reverse-proxy/public-route exclusion and
actual Synapse callback acceptance, then current-target-fenced queue/FCM worker,
Mobile receive/decrypt/reply and proved Starter ports. Research confirms Synapse
v1.164.1 RegularPolicyForHTTPS uses platformTrust; OpenSSL honors SSL_CERT_FILE.
Use an owned test CA rather than disabling certificate verification. Actual
provider fixture still has no callback configuration; earlier tests only register
pushers. A deployable listener is still required before enabling the callback flag.
No large-company throughput or full PRD group acceptance. Phone release10/c037;
next validated Mobile product11+, notify first, explicit10.77.77.3:5555 install-only.

## Durable callback admission proved — 1 November 2026

[Evidence](measurements/chat-push-callback-admission-2026-11-01.json):
`HandleCollaborationPush.accept` bounds32 keys/30s and owns separate lookup then
ordered current-authority transactions. Owned DIRECT events and quiet hints commit
one24h wake; temporary loss suppresses, permanent retirement/unknown keys reject.
Each key commits independently; retries preserve earlier committed heads. Late
caller/target expiry, backwards time, failed Result and interruption roll back.
Ten actual SQL cases plus49 domain policies and format/architecture pass in54s /
54.460s wall, four unchanged inputs. Four simultaneous duplicates and a two-company
partial batch are covered, as is waiting on an actual membership guard.

First43s run exposed a real omission of the separate transaction for transaction-
local key lookup (eight of nine new SQL cases failed). The use case now owns it;
RLS stays unchanged and the port comment explicitly states caller ownership.
Second46s nine-case pass is retained; final adds retirement/expiry branches.
No DI/private HTTP/worker delivery or live migration. **Continue immediately:**
private numeric source allowlist, bounded stream and cancellable admission executor,
actual API/worker contexts, then current-target-fenced FCM worker and Mobile reply.
Phone stays release10/c037; next validated Mobile product update11+, notify first,
explicit10.77.77.3:5555 install-only. All54 PRD groups remain unaccepted.

## Dispatch deadline and offline retention proved — 1 November 2026

[Evidence](measurements/chat-push-dispatch-deadline-2026-11-01.json) / [contract](collaboration/native-push.md):
FCM operation budget now stays separate from provider storage lifetime. Default
300-second retention is preserved; explicit RECOVERABLE permits up to24h with a
separate short dispatch deadline. Checks before/after OAuth, around complete FCM
I/O and after outcome mapping suppress late sends/results, including UNREGISTERED.
Actual signed OAuth returning after expiry never starts FCM; a real stalled body
is cancelled at the short budget. OAuth itself remains separately bounded10s.

20 core push cases pass in32s /33.015s wall; integration adds13 communications
policy, nine actual API and eight actual worker cases, format/architecture in76s /
76.499s wall. Nine source inputs unchanged. Intermediate8s task-selection failure
used an incorrect module prefix; fixed command only. Final core task is up-to-date
with focused source-matching reports. No live Firebase send or app source change.

**Continue immediately:** connect existing callback decoder/background policy to
schema116 queue with current admission, then private bounded HTTP ingress, fenced
worker delivery and Mobile receive/decrypt/reply. Do not reuse Ready30s as FCM TTL.
Periodic/reconnect/full-sync recovery and all-company ownership remain necessary;
24h storage is no receipt guarantee. No live migration or PRD group acceptance.
Phone stays release10/c037; next validated app product checkpoint11+, notify first,
explicit10.77.77.3:5555 install-only, then continue full P0–P8 work.

## Durable push queue and restore proved — 1 November 2026

[Evidence](measurements/chat-push-queue-2026-11-01.json) / [contract](collaboration/native-push.md):
schema116 stores one coalesced wake head per immutable device with64 bounded event
fingerprints, monotonic received/ack revisions and worker-only fenced leases.
Recent duplicates don't advance; later callbacks remain pending after an old ACK.
Quiet signals preserve visible identity and can recover after smaller retry budgets.
Eleven queue cases prove concurrent callbacks/claims, global16/per-call8 caps,
RLS, real expiry, stale/foreign leases, finite retries, rollback and actual restore.
Upgrade preserves a pre-existing encrypted binding; dump/restore keeps its key,
in-flight lease plus newer callback, normal RLS and next-worker continuation.
Recovery accepts116 with unchanged115 envelope query and rejects117.

Full schema regression passes:30 database +49 domain +137 selected data +38 actual
API +6 actual worker,24 recovery Python, format/architecture;357s /357.972s wall,
nine unchanged inputs. First45s run only needed explicit run<Unit> in interrupted
fixture; subsequent66s focused11-case pass also included the reviewed quiet-retry
refinement and real restore. Final run adds simultaneous worker claim contenders.
No DI, admitted callback, dispatch adapter, live migration or app source change.

**Continue immediately:** connect private callback/current-background admission
and durable queue, then fenced worker FCM delivery and Mobile receive/decrypt/reply.
Existing pure policy/decoder e5eebcd4, activation24841839 and safe provider logging
43c09384 are pushed. Queue code is now implemented; do not recreate earlier drafts.
`.work/push-callback-next.txt` records concrete seams and latest review findings.
Keep operation deadline (Ready30s) separate from FCM TTL; review explicit dispatch
deadline and recoverable offline hints before inheriting the current300s transport
limit. Startup/reconnect/periodic sync still needs explicit ownership. No exactly-
once FCM or large-company capacity claim; current16 global leases are pilot bounds.

API already has Tomcat5s socket timeout. Preserve attachment upload behavior while
bounding private callback stream/concurrency and enforcing numeric source allowlist;
ignore forged forwarded headers and prove stalled-body handling. A private TLS
proxy listener can provide its own tighter limits without adding another Tomcat
connector automatically. Reuse the existing owned InboxPushWorker task/timer/
DeadlineTask pattern with finite chat leases and parallelism. Validate actual API
and worker contexts for the upcoming DI change. All54 groups remain unaccepted.
Phone remains verified release10/c037. Next validated Mobile product checkpoint
uses11+, notify first, explicit10.77.77.3:5555 and install-only; then continue work.

## Provider push logging proved — 1 November 2026

[Evidence](measurements/chat-push-provider-logging-2026-11-01.json) / [deployable files](../deploy/synapse/README.md):
exact Python formatter/JSON logging configuration load in both actual Synapse
pusher fixtures. Final-output redaction covers message args, exception/stack/cache,
partial key namespace and output bounds; failure is fixed text, termination passes
through. Two provider + seven Python cases and format/architecture pass in 47s /
48.097s wall (six unchanged inputs). Original 60s pass used a live consumer;
review tightened this to a bounded final log snapshot covering restart/removal.
The intermediate missing docker-java core helper compilation failure is retained;
existing public API Adapter fixed it with no new dependency or formatter change.
Root check now includes the seven Python cases. No live configuration change.

**Continue immediately:** implement persistent coalesced callback/dispatch state,
restricted HTTPS callback ingress, and actual worker send/target-version fencing.
Policy/decoder e5eebcd4 and activation 24841839 are pushed. Use the existing narrow
background policy and decoder; retain short interactive access/MFA gates for content.
`.work/push-callback-next.txt` contains the concrete queue/guard/HTTP seams, copied
from the current review; it is a design draft, not implemented schema. Proposed
one head per immutable device, monotonic hint/ack revisions, bounded event receipts,
lease-fenced delivery, preservation of newer callbacks during network I/O, and
startup/full Matrix sync after missed/coalesced FCM. No exactly-once FCM claim.

Before accepting offline notifications, review the existing transport's 300-second
FCM TTL against longer disconnected devices and implement explicit reconnect/
periodic reconciliation or a reviewed longer store-and-forward hint profile.
Do not silently inherit Inbox's short lifetime as complete chat recovery. Stream
budgets also need an actual socket/private-proxy read timeout, since checking
elapsed time around a blocking read cannot interrupt the underlying socket.
Continue Mobile receive/decrypt/reply and verified Starter ports after backend.
Phone stays release 10/c037; next validated product code 11+, notify first and
explicit install-only to 10.77.77.3:5555. All 54 PRD groups remain unaccepted.

## Background hint boundary proved — 1 November 2026

[Policy/decoder evidence](measurements/chat-push-hint-policy-2026-11-01.json) / [contract](collaboration/native-push.md):
49 domain + five delivery cases and format/architecture pass (30s / 30.517s wall,
six unchanged inputs). First run's seven new-case failures were an Account fixture
version/securityVersion mix-up; the explicit securityVersion corrects it without
changing the policy. Both short access expiries and absent MFA allow only opaque
hints; actual interactive content admission remains denied. Current parent/device/
account/member/company/client/native-registration checks distinguish permanent
retirement from temporary suppression. Pending binding publication is identity,
not authority; late versions and deadlines are fenced. Strict 8 KiB/32-device
callback decoding excludes plaintext and handles Synapse's empty legacy badge
sentinels. Actual callback transport remains unproved. No DI/schema/live change.

**Continue immediately:** safe provider logging, restricted callback ingress and
bounded persistent hint/outbox state before provider acknowledgment; then actual
worker dispatch with current target-version checks, Mobile receive/decrypt/reply
and verified generic Starter ports. Pure policy/decoder are now implemented; wire
them into the owning use case, do not duplicate or weaken interactive admission.
Outgoing activation commit 24841839 is pushed. The full PRD remains active.
Phone stays installed release 10 / c0371a38; next validated Mobile product update
uses code 11+, notify first and explicit install-only to 10.77.77.3:5555.

## Managed pusher activation proved — 1 November 2026

[Contract](collaboration/native-push.md) / [evidence](measurements/chat-pusher-admission-2026-11-01.json):
fixed empty-body vendor POST uses current gateway/interactive admission, reserves
one encrypted opaque identity before unlocked provider I/O and publishes once.
Current native push registration/version/expiry are fenced around the call and
publication; response loss, interruption, concurrent calls, late permission loss,
disable/re-enable and token rotation are exercised. Default-disabled conditional
configuration validates the fixed HTTPS URL only when enabled. Standard
pushers/set remains denied, and clients never choose or receive keys/targets.
40 domain + 121 data + 38 actual API + six actual worker cases pass, along with
format/architecture (222s Gradle / 222.990s wall, 13 unchanged inputs). Preserve
all four preceding attempts: two compiler-fixture issues, unique fake-token seed
correction and the passing seven-case focused run. No production workaround.

**Continue immediately:** implement the narrowly scoped background push admission
policy (parent refresh lifetime/current authority, not short interactive access
expiry), private callback ingress and safe provider logging, durable callback/
feed/outbox, then Mobile receive/decrypt/reply and verified Starter ports. The
current outgoing source is implemented and validated; earlier scratch drafts are
historical. Do not reapply them. Keep the push flag off until callback/delivery
is ready. No live migration/provider rollout or PRD group acceptance has occurred.

Hosted five-case native run 41351236140 is now [audited](measurements/chat-cancellation-native-ci-2026-11-01.json)
at Mobile 302aa192 / Platform d48ac2f8: five native + six cleanup cases, 32 hashes,
APK 5438147b, all three recovery workers pass once. No hosted pending-key 503 was
observed; local earlier evidence separately exercised that branch. Mobile audit
3941bfc1 is pushed. Phone remains release 10 / c0371a38, untouched since 20:44 UTC.
Next validated Mobile product checkpoint is code 11+, notify first and install-only
to explicit 10.77.77.3:5555. Continue full P0–P8 after every atomic commit/push.

## Stable encrypted push binding and restore proved — 1 November 2026

[Storage evidence](measurements/chat-push-binding-storage-2026-11-01.json) / [contract](collaboration/native-push.md):
schema 115 stores one immutable encrypted/hash-looked-up pusher key per company,
account, native parent and provider device; only reservation-to-publication changes.
Owned RLS, competing guards, late denial/interruption rollback, missing/revoked
parents, ciphertext/context tampering and actual dump/restore with old/current
keyring and production recovery SQL pass. Full validation: 30 database, 40 domain, 221 data, 26 api, 6 worker,
format/architecture; 603.453s wall, nine unchanged source inputs.
33 Python recovery cases pass. The first 63s run had one invalid corruption
assertion: removing optional Base64 padding changed no decoded bytes; fixture
now removes bytes. Original failure is retained, product unchanged.

Key lifecycle decision: retain the same opaque key across FCM token/registration
rotation, then fence current native registration version at every actual delivery.
This refines earlier rotation-on-registration wording and prevents duplicate
pushers/stale remove-versus-re-enable races. Different immutable device means a
new key. Temporary disablement suppresses delivery; only permanent device/parent
retirement can remove the provider pusher. Lookup or publication is never current
chat authority. No DI/controller/live migration or delivery is claimed.

**Continue immediately:** integrate outgoing admission with the existing private
AccessNativeCollaborationMatrixDevice exchange runner and a fixed vendor extension
endpoint, conditional disabled-by-default push configuration, and real API/worker
context tests. Then implement private callback ingress, safe provider logging,
durable callback/feed/outbox, Mobile receive/decrypt/reply and verified Starter ports.
`.work/push-binding-next.txt` records concrete seams; `.work/pusher-admission-methods.txt`
is an uncompiled draft, not product code. Review/fix labels and current publication
time checks while applying. Never accept a client FCM key or callback URL.

Background hint admission needs its own narrow policy: existing interactive
collaborationParentDeadline requires short native access expiry. Do not silently
stop notifications after that token expires or weaken normal sync/reply/MFA access.
Resolve live parent, account/company/permission, device, room and native push target;
opaque hints grant no new read/write privilege. Preserve selected-company Mobile
ownership; no invisible workspace change. All 54 PRD groups remain unaccepted.

Mobile 2e4e1081 and Platform 929bb7cd are pushed; five-case native run 41351236140
at Mobile 302aa192 / Platform d48ac2f8 is still to audit. Phone remains installed
release 10 / c0371a38, untouched since 20:44 UTC. Next validated Mobile product
checkpoint uses code 11+, notify first, explicit 10.77.77.3:5555 and install-only.
Continue after commit/push; no checkpoint stop and no repeated phone build for
Platform-only changes.

## Matrix pusher transport proved; continue admitted bindings — 1 November 2026

[Contract](collaboration/native-push.md) / [evidence](measurements/chat-matrix-pusher-transport-2026-11-01.json):
fixed HTTPS callback/app ID, opaque server key, event-only content, explicit
idempotent removal, 8 KiB limits and existing owned/cancellable HTTP transport.
29 tests/three suites plus format/architecture pass (52s Gradle). Actual Synapse
retains two independent keys across restart, repeated registration/removal and
five-second provider credential expiry. The initial two fixture assertion failures
(Jackson whole-node map shadowing) are retained and corrected without changing the
product. No DI/public controller, callback delivery, schema or live service change.

Next implement encrypted/versioned company-account-session-device push bindings,
current registration/access admission, durable callback recovery/outbox and Android
receive/decrypt/reply. Capabilities are scoped to immutable device identity; actual delivery must fence
the current registration and never expose FCM tokens to Synapse or logs. Do not reuse
expiring gateway access as background authority or silently switch workspace.

Hosted release-10-source run 41347895253 at c0371a38 / old Platform bad75bd0 is now
[audited](measurements/chat-cancellation-ui-native-ci-2026-11-01.json): four native
cases, six cleanup cases, 26 hashes, proof APK 86d3115a; SDK 60.410s, scheduled
88.926s, queued/frozen pair 105.883s. It does not exercise dialog/cancelled-original
or pending-key correction. New five-case run 41351236140 at Mobile 302aa192 is in
progress; inspect its actual outcome later. Mobile audit 2e4e1081 is pushed.
Phone remains verified release 10, install-only; next app product checkpoint 11+.
Continue the full unaccepted PRD after each atomic commit/push; no checkpoint stop.

## Push transport profiles validated; continue registration — 1 November 2026

[Native push contract](collaboration/native-push.md) and
[measurement](measurements/chat-push-delivery-profile-2026-11-01.json) record explicit
background/visible delivery: Android data-only NORMAL/HIGH and generic Apple
background/alert. Inbox explicitly preserves its Android collapse identity, also
applied to Apple. 15 push + 13 domain + eight actual worker cases and format/
architecture pass (58s / 59.222s), six unchanged inputs. The first compile failure
and fix are retained. No DI/schema change, live FCM or incoming chat acceptance.

Previous Platform d48ac2f8 and Mobile 302aa192 are pushed. Mobile now pins d48 and
has a 45-minute native job. New five-case run 41351236140 was pending behind
41347895253 at c0371a38 (old four-case pin); inspect actual source/results separately.
Continue fixed Matrix pusher transport/registration, admitted durable callback
recovery, Android notifications and inline replies, then reusable Starter ports.
Never silently change the selected company for background work. All PRD work
remains active; the live provider deployment is unchanged.

Phone remains verified release 10 / c0371a38, installed 20:44 UTC without launch.
This Platform-only increment needs no new APK. Next validated Mobile product
checkpoint: code 11+, notify first, explicit 10.77.77.3:5555 and install-only.

## Cancelled-draft native proof and publication retry corrected — 1 November 2026

[Five-case evidence](measurements/chat-cancellation-native-2026-11-01.json): 356s Gradle / 357.121s wall,
68 Mobile/five Platform inputs, APK `3c15eb832190528e92cd85c8385e3367815cd6cc6ae809f7dd26525af4c17275`.
CANCELLED_DRAFT retains an authenticated cancellation and matching frozen draft through real
death beside one independent command. Replay does not schedule or create a receipt; recovery
clears only that draft, excludes its history and sends the independent original once plus
32 peer seeds. QUEUED/FROZEN_DRAFT, SDK and WorkManager remain separate passing cases.

The first run failed frozen-only after bounded delivery retries (409s / 409.864s); the other
four cases including cancellation passed. Trace: temporary key upload/query 503, then
sendToDevice 400/device_keys_required, later successful uploads but no room send. Real-SQL
reproductions confirmed a valid version 0 reservation was being classified as malformed.
It now returns UNAVAILABLE/device_keys_pending (HTTP 503), preserving confirmed-key/fingerprint
and current owner/permission gates. No reservation still gives validation; no trust, schema,
DI or operation-deadline change. Three focused cases pass; full 40 domain/210 data/26 HTTP,
format/architecture pass 423s / 424.148s. Initial two failed reproductions and full native
failure are retained. The final QUEUED native case actually receives one pending-key 503, retries in two worker passes and completes exactly 33 encrypted sends; frozen/cancelled cases each take one pass. All cleanup succeeds and the owned emulator is stopped. Mobile proof/quality passed 145s / 148.995s, cleanup six; 69 Python cases pass.

Hosted [448ab3ad run 41344248284](measurements/chat-frozen-native-ci-2026-11-01.json) and
[648410c4 run 41345994459](measurements/chat-cancellation-policy-regression-native-ci-2026-11-01.json)
are audited separately, 26 hashes each. Both use the earlier four-case Platform fixture;
neither proves this newer native cancellation/pending-publication boundary. UI run 41347895253
at c0371a38 was still running with the old pin; audit its actual source if it completes.

**Continue immediately:** commit/push the corrected Platform fixture/product/evidence; pin its
exact SHA in Mobile collaboration-hris CI and give the full job 45 minutes (old four-case hosted
job took 29m30; operation/test retry limits stay unchanged). Commit/push Mobile fixture/docs,
then implement FCM/pusher admission, opaque durable notification recovery and native inline
replies. Platform `.work/collaboration-push-next.txt` records official Matrix v1.16 and current
transport/owner findings; move concrete implementation contracts into Git. Current FCM mapper
is inbox-only NORMAL/silent-Apple; current Mobile runtime and inbox push use selected company.
Do not silently switch workspace or claim all-company background support. Notifications must
use current account/company/device/room admission and the same encrypted message outbox.
Port only verified generic notification/scheduler primitives to Starter. All 54 groups remain
unaccepted; continue all P0–P8. Live backend/provider deployment is unchanged.

Phone remains verified release 10, source c0371a38, installed 20:44 UTC over 9 with exact pulled APK,
existing signer/data and no UI launch. These new Mobile edits are fixtures only, so another
phone build is unnecessary. Next validated app-feature install must use code 11+, notify first,
install-only on 10.77.77.3:5555. Never drive the phone UI; use the owned emulator for tests.

## Recovery-era hosted run audited; Mobile release 9 installed — 1 November 2026

[Hosted audit](measurements/chat-draft-recovery-native-ci-2026-11-01.json) verifies Mobile
41340935053 at 1ddfba6e / Platform 79326538: SDK 54.085s, direct worker 63.689s and
scheduled worker 62.336s, six cleanup cases, twenty evidence hashes. APK SHA-256
`cd32c3984577324f919dee9eaa14e0e65bcb881f47fd2544aefc150ad24263c7`. Ordinary queued recovery
retains 33 encrypted originals, four history actions, 43 references/10 state-only and zero
admission failures. WorkManager automatically recovers ordinary death (SUCCEEDED/attempt 1).
This source contains app draft recovery but the fixture starts with a queued command;
explicit frozen-only proof belongs to the later four-case fixture and its separate evidence.
Run 41344248284 at Mobile 448ab3ad / Platform bad75bd0 is now running that newer fixture.

Mobile release 9 from 448ab3ad is installed and verified in place on the authorized phone at
20:01 UTC, with existing signer/data and no UI launch. Technical draft contention feedback
is fixed and its complete checks are committed. Mobile is now implementing queued-only
cancellation with authenticated immutable archives and full-record arbitration against
staging; storage/domain/application tests are in progress. Continue confirmation UI, incoming
push/replies and all remaining P0–P8. Live backend/provider deployment is unchanged; no full
PRD group is accepted. Keep latest detailed product/build evidence in Mobile's handoff.

## Native frozen-draft recovery proved; continue — 1 November 2026

[Source and artifact evidence](measurements/chat-frozen-native-2026-11-01.json) records actual
Android/Synapse/HRIS execution with proof APK `1a6645456001c0c06c1db8ec12ed405c2d9015986ff7366ca9fe32e1b2d2363d`.
Final combined run: 227s Gradle / 227.82s wall, four tests/three suites, zero failures. SDK
suite 48.362s, scheduled 44.822s, worker pair 110.945s (ordinary 50.339s/frozen 40.341s methods).
Both worker cases preserve original identity through real death, send once and acquire 33
originals, with four history actions, 43 references/10 state-only, 30/28/30 admissions, zero failures.
Frozen checkpoints prove zero commands and the same draft revision on both sides of death;
confirmation clears the draft. All cleanup succeeds; owned emulator is stopped.

Initial native run had SDK/queued passes, but PREPARED was accidentally excluded by the
new shared validator and frozen setup failed at provider login before its Android driver.
Both are retained: restored PREPARED with a scheduler-handshake test; provider and Spring
trace are now isolated per test. Exact initial provider failure code was lost, so do not
claim an observed 429. Safe kind/code diagnostics now preserve future setup failures.
Python 66 cases and Platform test compilation/format/architecture pass (28s/28.739s).
Mobile proof/quality passes 80s/84.218s after splitting a fixture method rejected by detekt;
that earlier 141s/144.762s quality failure is retained. No product/runtime workaround.

**Continue immediately:** commit/push Platform, pin its exact commit in Mobile native CI,
commit/push Mobile fixture/evidence and audit the hosted result. Release 8 is installed from
Mobile 43cce8c0; no new phone build is needed for these fixture changes. Then fix the reviewed
composer distinction: technical checkpoint contention currently uses draft_changed, which
makes the UI ask to reload despite unchanged target text. Use a separate retryable busy
code and preserve true conflicting-edit reload; validate and continue all P0–P8. No complete
feature group or live provider deployment is claimed. Draft recovery implementation is
Mobile 1ddfba6e; its root/browser/Android evidence is linked in the delivery ledger.

## Composer-era native regression audited — 1 November 2026

Mobile [run 41338026431](https://github.com/fajarxfce/hris-mobile/actions/runs/41338026431)
at `cca28f06` / Platform `79326538` passes SDK/direct/scheduled cases in
42.32s / 56.409s / 59.092s, proof APK `c8185d69`. [Audit](measurements/chat-draft-composer-native-ci-2026-11-01.json)
binds twenty downloaded files, including sanitized gateway observations, plus six cleanup
cases. Existing 33-original foreground/worker and automatic WorkManager recovery assertions
pass. Final exhausted-retry diagnostics are now retained, but this success does not explain
the earlier bf83 failure or change its unknown direct-delivery outcome.

This source includes composer-v1 UI; these native fixtures exercise the existing worker and
foreground route, not the draft transition boundaries. Mobile composer-v2/indexed recovery is
newer and undergoing root/browser/Android checks with its own freeze-only restart tests.
Live Platform/provider deployment remains unchanged; no feature group is marked complete.
Continue Mobile release 8+ install-only after validation and the remaining communication PRD.

## Later bounded-window native run audited — 1 November 2026

Mobile [run 41335935547](https://github.com/fajarxfce/hris-mobile/actions/runs/41335935547)
at `f9041e62` / Platform `79326538` passes all SDK/direct/scheduled cases in
53.186s / 56.09s / 82.571s with proof APK `85583fca`. [Audit](measurements/chat-window-native-ci-2026-11-01.json)
binds 18 downloaded files and retains the failed bf83 run 41334029287. The direct worker
completes in one pass, confirms 33 encrypted originals, four history actions, 43 references,
ten state-only references and 30/28/30 admissions with zero failures. Only the oldest original
is explicitly checked after Stop. WorkManager ordinary-death recovery remains automatic,
SUCCEEDED/runAttempt1, with restored network and complete cleanup. SDK isolation and six
cleanup tests also pass.

This includes the bounded-window source and inactive composer-v1 storage. It does not test
603 originals on Android or the newer composer UI/v2/automatic draft recovery. The pass does
not explain the prior exhausted-retry failure; newer native runs retain controlled categories
and sanitized gateway observations for diagnosis. No Platform production code, schema or
live deployment changed. Mobile is now running full integration for draft recovery; release 7
is still installed. Continue the long run and all remaining communication groups.

## Later native window run retained as failed — 1 November 2026

[Run 41334029287 audit](measurements/chat-window-ci-failure-2026-11-01.json) binds 16 files
at Mobile `bf83c71e` / Platform `79326538`, proof APK `8d391c22`. SDK 47.387s and scheduled
56.862s pass; direct worker fails in 163.103s after exhausting four attempts, before foreground
assertions. Driver cleanup succeeds, but the final retry category was discarded by a generic
check. Direct delivery is unknown. This newer failure is not overridden by run 41331799148.

Platform already writes a sanitized `gateway-observations.json` in the direct fixture's
finally block. Mobile's upload allowlist omitted it. The next Mobile fixture change retains
the final controlled failure category and uploads that existing bounded file. Investigate the
resulting evidence before changing runtime retry limits or declaring the native window accepted.
No Platform code/schema/deployment change follows from this audit. Release 7 remains installed;
Mobile draft storage and local policy are pushed with JVM/browser validation, UI/recovery next.

## Hosted foreground/native acceptance audited — 1 November 2026

Mobile [run 41331799148](https://github.com/fajarxfce/hris-mobile/actions/runs/41331799148)
passes with Mobile `2b2d81b3` and Platform `79326538`, APK `d94ba860`, Android API 35.
[Audit](measurements/chat-foreground-ci-2026-11-01.json) binds 18 downloaded artifacts and
retains predecessor 41325612922: SDK/scheduled passed there, direct worker failed ClosePending
before returning its result, so its delivery outcome remains unknown.

Current SDK/direct/scheduled cases pass in 56.228s/67.626s/90.851s. Direct worker recovery
and real foreground ViewModel acquire 33 encrypted originals with four history actions,
43 references and ten state-only references, admissions 30/28/30 with zero failures and
complete cleanup. All originals are asserted before Stop; only the oldest row is explicitly
asserted after Stop. SDK retains three sends and held-room isolation. WorkManager retains
its namespaced CONNECTED job with task-only input through ordinary death, starts automatically,
restores networking and ends SUCCEEDED. Six fixture cleanup unit tests also pass.

Mobile `bf83c71e` subsequently implements bounded 150/50 bidirectional local windows and
passes full local checks. Its release 7 is installed over code 4 on the authorized phone;
no uninstall/data clearing/UI launch. This audit has the earlier immutable Mobile source;
run 41334029287 covers the new window separately. Generic query port `cd7022e` is pushed to
Starter. Live backend/provider is unchanged. Continue Apple database execution, durable
composer/submission recovery, safe cancellation, push/replies and all unaccepted P0–P8.

## Native foreground history and publication ordering — 1 November 2026

The companion Mobile fixture now exercises the actual ChatThreadViewModel after
worker recovery, using 32 encrypted peer seeds. Platform schema 114 fixes an observed
key-discovery race: read-only discovery verifies keys against the committed reservation
before publisher readback is recorded. Claims, to-device submission and room messages
retain confirmed-publication requirements. Every provider identity remains signature/
fingerprint checked, with current authority and late-result fences.

[Contract](collaboration/key-publication-race.md) and
[measurement](measurements/chat-foreground-native-2026-11-01.json) retain 10 Mobile/13
Platform source hashes and all attempts. The same APK `d685231d` failed before and
passed after the server correction, with no forced key refresh: 94s, one worker send,
32 peer seeds, four older actions, 43 references, ten state-only references, 33 canonical
originals acquired and the oldest row retained after Stop. Admissions 30/28/30, zero failures, cleanup complete.
The SDK and actual WorkManager regressions both pass in a combined 120s: three/one
sends, held room isolated, automatic ordinary-death recovery and network restoration.

Final Mobile checks pass: 2,733 desktop cases/427 suites, six cleanup tests, APK/lint/
format/architecture/detekt. Platform's final 110 registration/crypto-admission and 26
API cases pass. Forty domain cases and 97 unchanged data cases passed in the earlier
broad run, whose three failures remain recorded and were corrected in the 110-case
rerun. Seven of those data cases cover schema 112→113→114 preservation and actual
restricted-role dump/restore. Recovery's 11 cases accept 114 and reject 115; 63 driver
cases pass. The temporary forced-refresh experiment and mistaken status expectations
are recorded separately. Emulator stopped; no live deployment or Starter change.

**Continue the long run.** Pin this Platform commit in Mobile's native CI, run/upload
its proof cleanup tests, push the companion commit, and audit hosted artifacts. Then
continue bounded local browsing beyond ten pages, durable drafts and safe cancellation,
incoming FCM/replies, richer messages, key continuity/backups and calls/meetings. No
full PRD group is accepted. Release 6 remains ready but uninstalled; the latest phone
ADB attempt still timed out. Last confirmed installed code was 4. Use the build6
preflight/install-only helpers, notify first, preserve signer/data and do not drive
phone UI. Device availability does not block implementation.

## Release 6 prepared and hosted native evidence audited — 1 November 2026

Mobile `97ef0639` records release 6 from product commit `2d6753ee`, ready but **not installed**.
Use `.work/release-phone-2026-11-01-build6/` in Mobile, including current preflight/install-only
helpers. APK SHA-256 `63cba8dccec9c50e73b13f39f4645304b98cec7b84d79b8091f5f5e9299d27ba`,
87,264,632 bytes, arm64, version 1.0.0/code 6. Signer/API/Firebase/VCS/alignment checks pass;
non-debuggable/non-testOnly; 295s build, zero lint errors/one existing warning. Phone ADB
still times out; last confirmed installed version is 4. Notify before installation and
preserve exact signer/data identity. Do not use the older build5 helpers or drive phone UI.

[Hosted native evidence](measurements/chat-native-ci-2026-11-01.json) audits run `41322946959`
at Mobile `1a114442`/Platform `075e63db`, APK `9a42e773` on API 35. Scheduled/SDK/direct-worker
cases pass in 70.507/61.405/27.600s: ordinary-death automatic scheduling, sender restoration,
exact original/completion, one/three/one encrypted sends, no admission failures, complete
cleanup and restored network settings. Earlier `41319810722` ClosePending failure remains
recorded; this later pass does not prove it fixed. Foreground paging from `2d6753ee` is not
part of that hosted proof. Continue native fixture drain/error preservation and foreground
ViewModel/provider evidence, bounded local browsing, drafts/cancellation and all P0–P8.
Mobile `.work/native-foreground-next.txt` is design only. No live release or Starter change.


## Bounded foreground remote history — 1 November 2026

Mobile `2d6753ee64f4616adec6ad05783ad2d197a535e9` is pushed. The thread now pages provider
references and acquires bounded original windows, with owned offline reads, separate paging
errors/loading, explicit exact-anchor recovery and cancellation/denial guards. State-only
references do not open message focuses or imply missing messages. Concurrent provider growth
keeps the scan open. [Contract](https://github.com/fajarxfce/hris-mobile/blob/2d6753ee64f4616adec6ad05783ad2d197a535e9/docs/collaboration-remote-history.md)
and [measurement](https://github.com/fajarxfce/hris-mobile/blob/2d6753ee64f4616adec6ad05783ad2d197a535e9/docs/measurements/chat-remote-history-2026-11-01.json)
include all attempts, 18 source hashes and a visually inspected 320dp/200% text screenshot.
Final checks passed: 2,733 desktop cases/427 suites, 368 domain-browser cases, 70 presentation-
browser cases and 170 Android-host cases; zero failures/errors/skips. Platform runtime and
Starter are unchanged. No complete PRD group is accepted.

Continue actual native foreground paging evidence, bounded sliding/bidirectional local
browsing beyond the existing ten-page window, durable drafts, safe cancellation and all
remaining P0–P8. First prepare the updated mobile release and fix/audit native fixture
cleanup: hosted `41319810722` passed SDK but failed direct-worker instrumentation at
`HrisWorkerProofContainer.close` with ClosePending. Driver cleanup succeeded; no worker
result was read, so do not infer delivery acceptance. Preserve primary errors and use the
existing bounded close-drain contract. Newer native run `41322946959` is still active and
must be audited. Release 5 is prepared but uninstalled; phone WireGuard ADB still times out
and the reconnect question is pending. Continue independently, notify before install-only,
preserve signer/data and push every validated atomic commit.


## Actual Android WorkManager execution — 1 November 2026

Mobile `6f5ff9223193ca486dd61d0b84d5a16c866b93de` is pushed. The separate normal proof Activity queues while its
owned emulator is offline; read-only WorkSpec/system-job checks confirm task-key-only
input, the production SyncWorker and CONNECTED constraint. The Activity is backgrounded,
its PID is killed without force-stopping the package, and the job is retained. Restored
connectivity causes OS process startup and actual factory/task execution. The observer
never calls the worker. Exact original history/completion and one encrypted gateway event
establish delivery; radio settings and owned package resources are cleaned up.

[Contract](https://github.com/fajarxfce/hris-mobile/blob/6f5ff9223193ca486dd61d0b84d5a16c866b93de/docs/collaboration-workmanager.md)
and [measurement](measurements/chat-workmanager-2026-11-01.json) bind six Mobile/four Platform
inputs. Scheduled run passes in 76s (two processes/one ordinary death/one encrypted send,
zero admission failures). Both previous native scenarios pass in a combined 112s on APK
`3b5eb0d5`: sender recovery retains four processes/three deaths/three sends and no held-room
send; the direct worker retains one send/peer decryption. All 62 Python cases pass. Server
compile/quality: 28s; final Mobile build/lint/quality: 85s; root checks: 57s, 2,710 desktop
cases/426 suites, zero failures/errors/skips. The first run's fixture namespace timeout is
retained: WorkManager correctly held the job offline, while the observer queried only the
default namespace. Namespace-aware correlation/inspection fixed the fixture. Emulator stopped.

**Continue remote older history, durable drafts and safe pending cancellation, then all
P0–P8.** Deliberate interruption during sending, retry/cancellation races, production login
recovery, incoming FCM/replies, Apple/web and scale remain separate acceptance. No full PRD
group, live provider change or Starter port. Pin this fixture commit in Mobile's native CI,
add the scheduled test and safe stopped/started/radio evidence, and retain both previous
native tests. Audit hosted artifacts separately. The user still wants phone progress
installed during the long run: release 5 is prepared, but WireGuard ADB is unavailable and
the reconnect question is pending. Do not stop implementation; keep pushing atomic commits.


## Actual native application-worker interoperability — 1 November 2026

Mobile `ccc7c4fc5bd91f2ae99e3133b932dcdfa8f8ecd8` is pushed. The separate Android proof resolves the real shared
application worker/SyncTask with encrypted Android stores, protected SDK records and real
HRIS admission. Exact Queued UUID/text survives a confirmed PID death; the second process
proves the worker restores the closed sender, sends one encrypted event, acquires original
history and retires completion. The peer decrypts the original body/logical ID. A repeated
worker pass sends nothing. The new strict trace counter recognizes a conversation-summary
GET separately; it does not count that read as direct-conversation admission.

[Contract](https://github.com/fajarxfce/hris-mobile/blob/ccc7c4fc5bd91f2ae99e3133b932dcdfa8f8ecd8/docs/collaboration-native-worker.md)
and [measurement](../docs/measurements/chat-native-worker-2026-11-01.json) retain seven Mobile/four
Platform source hashes and all attempts. Worker test passes in 70s: two processes, one
death, one encrypted send, zero early sends/admission failures and cleanup confirmed.
The prior native sender regression passes in 78s: four processes, three deaths, three
sends, held room never sent, 22/22/22 admissions, zero failures. Both use proof APK
`924cdd45` on the dedicated API-35 emulator. All 51 Python driver cases pass; server
compile, format and architecture pass. Mobile root checks complete with 2,710 desktop
cases/426 suites, no failures/errors/skips. An initial release packaging failure and
successful unchanged-code retry are recorded separately. The emulator is stopped.

**Continue actual WorkManager lifecycle evidence now, then all P0–P8.** Direct invocation
of the actual worker is not proof of Android scheduling/automatic OS startup. Native
fixture login/workspace inputs are fictional; no live provider/phone/Starter change or
complete PRD group is accepted. Add both native scenarios to Mobile CI after pinning
this Platform fixture commit; its pending workflow draft also adds apps.shared triggers
and safe evidence uploads. Never upload private fixture.json. Continue remote history,
drafts/cancel, push/replies, media/actions, keys/backups, Apple/web, calls and meetings.
The user requested phone progress installs during the long run, but WireGuard ADB is
currently unreachable. Mobile is preparing release build 5 and must install-only when
connected, preserving signer/data; last confirmed phone build is 4. Keep working and
push each validated atomic commit.


## Latest-head capacity recovery — 1 November 2026

Mobile `4213d5f5926ae5f299a0fd1969ceb73f181df4c4` is pushed. Capacity recovery without a receipt anchor now
reads a scoped SDK head, validates owner/device/room/event and re-admits the exact room
before shutdown/restoration. An absent head changes nothing. The delivery worker can
recover a lost native ACK plus full history focus without another enqueue/resume;
initial runtime-opening/close-pending contention backs off. Explicit foreground Refresh
reopens history and reacquires a child while preserving owned local content. Cancellation
and changed admission prevent late restore/publication; no enrollment/bootstrap is added.

[Contract](https://github.com/fajarxfce/hris-mobile/blob/4213d5f5926ae5f299a0fd1969ceb73f181df4c4/docs/collaboration-history-recovery.md) and [measurement](https://github.com/fajarxfce/hris-mobile/blob/4213d5f5926ae5f299a0fd1969ceb73f181df4c4/docs/measurements/chat-history-recovery-2026-11-01.json)
bind nine matching source inputs. Focused checks pass in 169s; full checks in 240s:
2,710 desktop / 426 suites, 358 domain browser, 58 presentation browser, 170 Android
host, 14 worker and two binding tests, with zero failures/errors/skips. Relevant Android
and Wasm builds and quality checks passed; lint has zero errors and one existing warning.
APK `98cd76d4`, AAR `0bba8390`, proof APK `75a576d1` are built, not installed. Initial
fixture formatting and successful incremental compiler-cache fallback are recorded.
No new actual-server/SDK/WorkManager/emulator, Apple runtime, phone/live deployment or
Starter change. Simulated native/wire integration is not native provider acceptance.

**Continue actual native worker interoperability now, then all P0–P8.** Reuse the actual
worker with protected Android stores and real HRIS/SDK adapters; prove a queued command
survives process death before worker execution, exact encrypted delivery, original-history
recovery and retirement. Preserve earlier provider evidence as separate runs. A direct
worker proof still requires separate WorkManager lifecycle acceptance. Remote history,
drafts/cancel, push/inline reply, groups/media/actions, key continuity/backups, Apple/web,
calls and meetings remain. No full PRD group is accepted. Keep pushing atomic increments.


## Application-owned background chat delivery — 1 November 2026

Mobile `9bf2c5324fa6801dcdb1020f4058dd8164be038c` is pushed. Exact encrypted intent now wakes a singleton
application SyncTask; startup and ownership renewal also request its key. The worker
scans selected-company commands, uses an encrypted generation/CAS cursor for restart-safe
room fairness, and bounds each pass/room/staging batch. It replays exact room operations,
requires explicit failed-room retry, recovers original evidence before uncertain native
resume, and retires only proven completion. A blocked room cannot stop healthy batches.
The thread now queues/observes and wakes the worker; native sending no longer belongs
to a destination. Scope loss/cancellation release late native children.

[Contract](https://github.com/fajarxfce/hris-mobile/blob/9bf2c5324fa6801dcdb1020f4058dd8164be038c/docs/collaboration-background-delivery.md) and [measurement](https://github.com/fajarxfce/hris-mobile/blob/9bf2c5324fa6801dcdb1020f4058dd8164be038c/docs/measurements/chat-background-delivery-2026-11-01.json)
bind 32 matching source inputs and eight attempts. Full checks passed in 320s: 2,700
desktop / 426 suites, 352 domain / 233 data / 56 presentation browser, 170 Android host,
12 worker integration and two binding cases, with no failures/errors/skips. Android lint
has zero errors and one existing warning. APK `4e47ea8c`, AAR `97a1280f`, proof APK
`17ad74cc` are built, not installed. Worker integration uses real encrypted storage and
simulated HTTP/native fixtures; container restart is not Android process-death evidence.
No new server/emulator/Apple runtime, phone/live deployment or Starter change.

**Continue latest-head capacity recovery and actual Android worker interoperability now,
then all P0–P8.** [Next work](https://github.com/fajarxfce/hris-mobile/blob/9bf2c5324fa6801dcdb1020f4058dd8164be038c/docs/collaboration-delivery-next.md) identifies the unknown-anchor
recovery gap, repeated incoming-history capacity, and reuse of the actual worker in a
separately identified native proof. Remote paging, durable drafts/cancel, incoming push
and inline replies, groups/rich content/actions, cross-family keys, Apple/web adapters,
calls and meetings remain. No complete PRD group is accepted. Continue long run and push
each validated atomic commit; do not stop at this checkpoint.


## Owned text thread and app navigation — 1 November 2026

Mobile `b78553c3b7b093e6a106e279ca432a3c02dc8453` is pushed with the company-gated Chat service,
inbox/participant/setup/thread navigation, owned local history and exact send-intent
recovery. Projection binds original sender/content and complete native device/transaction
identity; copied logical IDs cannot merge unrelated messages. Entry retains company
denials across cancellation and closes late child timelines. A thread owns finite
foreground staging/delivery and original-history receipt recovery; confirmed-only
queues do not reopen delivery. Application ownership retains the parent SDK.

[Contract and reviewed fictional screenshots](https://github.com/fajarxfce/hris-mobile/blob/b78553c3b7b093e6a106e279ca432a3c02dc8453/docs/collaboration-thread.md) and
[measurement](https://github.com/fajarxfce/hris-mobile/blob/b78553c3b7b093e6a106e279ca432a3c02dc8453/docs/measurements/chat-thread-2026-11-01.json) bind 30 matching source inputs and
all six attempts. Focused checks pass in 88s, full checks in 232s: 2,660 desktop /
421 suites, 332 domain browser, 55 presentation browser, 170 Android host and no
failures/errors/skips. Android lint has zero errors and one existing warning. APK
`2339d0a5`, AAR `bfab470b`, proof APK `52633bd5` are built, not installed. No new
actual-server/emulator/Apple runtime, phone/live deployment or Starter change.

**Continue platform-scheduled delivery immediately, then all P0–P8.** The
[next implementation](https://github.com/fajarxfce/hris-mobile/blob/b78553c3b7b093e6a106e279ca432a3c02dc8453/docs/collaboration-delivery-next.md) uses existing SyncTask/WorkManager,
key-only scheduler payloads and encrypted commands as the work source. Add bounded
owned work discovery, durable wakeups, worker orchestration and startup/ownership
recovery, then remove route-owned transport. Local Older covers acquired history only;
drafts remain volatile. No full PRD group is accepted. Continue the authorized long
run and push every validated atomic commit.


## Trusted local sender identity — 1 November 2026

Mobile `5c5b2a81395d2b629e62cacfadbb05ef6cafe031` is pushed. The Android adapter reads only the initialized,
protected SDK record and returns Matrix identity without SDK client/directory/network
acquisition or credential mutation. Owned local identity is encrypted under account/
company scope, with first-write/exact-replay publication and existing history key-loss
protection. Native-family replacement preserves identity metadata, not cryptographic
key continuity. [Contract](https://github.com/fajarxfce/hris-mobile/blob/5c5b2a81395d2b629e62cacfadbb05ef6cafe031/docs/collaboration-sender-identity.md) and
[measurement](https://github.com/fajarxfce/hris-mobile/blob/5c5b2a81395d2b629e62cacfadbb05ef6cafe031/docs/measurements/collaboration-sender-identity-2026-11-01.json) bind
29 source inputs and all attempts. Focused checks pass in 162s, full checks in 203s:
2,622 desktop / 417 suites, 320 domain browser, 226 data browser, 170 Android host,
zero failures/errors/skips and zero Android lint errors. Seven new storage cases
run on SQLite/IndexedDB; nine domain and five Android host cases pass. APK `51944ab4`
and proof APK `fe5b22b5` are compiled, not installed. No new actual-server/emulator or
Apple runtime proof, phone/live change, Starter change or full PRD group acceptance.

Continue the message thread, app routes, owned delivery scheduling and all P0–P8.
Canonical history and pending intent must match complete trusted sender/content
identity before UI merging; a peer-controlled logical ID is insufficient. Keep offline
local access separate from setup/live sending, and preserve explicit runtime/child
lifetimes. Continue the authorized long run and push each validated atomic commit.


## Recoverable Chat setup — 1 November 2026

Mobile `980c7d76ba3d7555d9d584bee47951629ad1eeab` adds an owned setup page with explicit activation,
provisioning wait/retry, retained-operation continuation and readiness. Entry does
not enroll or bootstrap. Confirmed registration/job receipts reconcile without
resending; confirmed bootstrap installs the protected runtime and retires control
before publishing device readiness. Application DI registers inbox, participants
and setup. [Contract/screenshots](https://github.com/fajarxfce/hris-mobile/blob/980c7d76ba3d7555d9d584bee47951629ad1eeab/docs/collaboration-setup-presentation.md) and
[measurement](https://github.com/fajarxfce/hris-mobile/blob/980c7d76ba3d7555d9d584bee47951629ad1eeab/docs/measurements/chat-setup-2026-11-01.json) retain all attempts and
21 matched inputs. Full validation takes 330s: 2,606 desktop, 311 domain browser,
32 presentation browser, zero failures/errors/skips and zero Android lint errors.
APK `7d5df852` is built, not installed. No phone/live/Starter change.

Continue trusted local sender identity, thread composition, app navigation and
owned delivery, then all remaining P0–P8. No full PRD group is accepted. Bootstrap
build/platform binding, expired issuance, cross-family keys and lost-key recovery
still need explicit protocol work. Identity caching must not be described as key
continuity. Keep working and push each validated atomic commit.


## Participant discovery and durable direct opening — 1 November 2026

Mobile `5f03349facc328e1ef1d3071418f472909ad1d20` is pushed with company-admitted participant search,
explicit paging and retained Open preparation/replay. Point summary acquisition
shares the local list publication mutex; exact peer/version reconciliation precedes
navigation. Lost responses reuse the same command, and a confirmed operation is not
resent after a failed summary read. App DI resolves both Chat ViewModels without
network work. [Contract and screenshots](https://github.com/fajarxfce/hris-mobile/blob/5f03349facc328e1ef1d3071418f472909ad1d20/docs/collaboration-discovery.md) and
[measurement](https://github.com/fajarxfce/hris-mobile/blob/5f03349facc328e1ef1d3071418f472909ad1d20/docs/measurements/chat-participants-2026-11-01.json) retain 21 source
hashes and all attempts. Full validation takes 401s: 2,577 desktop, 295 domain browser,
21 presentation browser, zero failures/errors/skips and zero Android lint errors.
APK `3a093eba` is built, not installed. No phone/live/Starter change.

Continue setup recovery, message thread, app navigation and owned scheduling, then
all remaining P0–P8. These pages are DI-registered but not yet exposed as a Home
service. No complete PRD feature group is accepted. Preserve encrypted SDK identity
across explicit recovery and never replace an uncertain bootstrap operation silently.
Keep the authorized long run going and push each validated atomic commit.


## Hosted retirement control and Apple build — 1 November 2026

[Control run 41301133469](https://github.com/fajarxfce/hris-mobile/actions/runs/41301133469)
passes all 28 steps at Mobile `070d3ab5`: 523 desktop / 58 suites, 499 browser /
54 suites and 407 iOS simulator / 46 suites, zero failures/errors/skips. The downloaded
log contains one Xcode BUILD SUCCEEDED marker. All 308 recorded Mobile source hashes
match that immutable commit. The [retirement measurement](measurements/collaboration-retirement-2026-11-01.json)
retains this evidence separately from the actual Android four-process proof. It is
not evidence of Apple message transport interoperability or of the later Chat UI.

The first Chat inbox module is now pushed at Mobile `829b2566065cc78ea289445dc416c692dc63a6e5`;
its [contract and reviewed screenshots](https://github.com/fajarxfce/hris-mobile/blob/829b2566065cc78ea289445dc416c692dc63a6e5/docs/collaboration-presentation.md)
and source-bound measurement cover local ownership, paging, cancellation and layout.
Participant discovery, setup, thread, app navigation and all remaining P0–P8 work
continue. No complete PRD feature group is accepted and no phone/live change occurred.


## Hosted HRIS retirement proof — 1 November 2026

[Run 41301133411](https://github.com/fajarxfce/hris-mobile/actions/runs/41301133411)
passes at Mobile `070d3ab5f718229ab88dcc7b0a36ad3cd1f5a346` with Platform fixture
`f80be73987224fda12d5a3a08a96ec6716538cc9`: all 17 steps, 165 Android host cases
and the actual HRIS emulator case, zero failures/errors/skips. Fixture
`7d92ab3729f544daa05111bc4df00a75` takes 50.609s (suite 94.355s), confirms completion
cleanup after the third forced death, exactly three encrypted sends, no held-room
send, 22/22/22 successful admissions and cleanup true. All 308 recorded Mobile and
33 Platform source hashes match those immutable commits; the extra Mobile input
relative to the 307-file local pre-build set is the final HRIS workflow pin.

The existing [retirement measurement](measurements/collaboration-retirement-2026-11-01.json)
now retains the independently checked hosted results. Chat presentation remains in
progress; this proof accepts no complete PRD feature group. Continue P0–P8 and push
each tested commit. No phone or live service changed.


## Completion retirement process-death proof — 1 November 2026

Actual Android/HRIS run `90ac7444ccff4f258b1a78f6c076a226` passes with four distinct
processes and three forced deaths, including after command archival and before
journal cleanup. The fourth process uses only owned local evidence, recognizes
archived receipts, cleans exactly two journal entries, reopens SQLite and rejects
changed-intent replay. Server still sees exactly three encrypted sends and no
held-room send; admission is 22/22/22 with zero failures. Native case 43.141s,
suite 65.560s, Gradle 95s; cleanup true. Driver 45 cases pass, fixture/quality 31s.
See [measurement](measurements/collaboration-retirement-2026-11-01.json): 307 Mobile
and 33 Platform source hashes match, proof APK `9e9dcdbd`.

Mobile implementation [`070d3ab5`](https://github.com/fajarxfce/hris-mobile/commit/070d3ab5f718229ab88dcc7b0a36ad3cd1f5a346)
is pushed; its workflow pins validated Platform fixture `f80be739`. All 307 source
hashes match the immutable Mobile commit. Focused checks pass in 150s, final native
fixture build/quality in 55s. Full Mobile root/browser/Wasm/Android/proof/lint passes
in 197s: 2,535 desktop / 165 Android host / 280 domain browser / 219 data browser /
35 database browser, zero failures/errors/skips and zero lint errors. Dev APK
`5c9487c1` is built, not installed; proof APK still `9e9dcdbd`. No phone/live change;
dedicated emulator stopped. This checkpoint does not accept a PRD feature group.
Continue Chat UI scheduling, Apple/web, backup, rich messages and all calls/meetings.
Keep every atomic commit pushed; do not stop at this checkpoint.

Prior CI audits are recorded separately: ACK recovery control `41295813930` passes
484 desktop / 460 browser / 389 iOS simulator cases and Xcode build, with 279 hashes
matched. Starter archive Apple run `41298641454` passes 84 simulator cases, all 16
Apple steps and Xcode build, with 15 hashes matched; its other jobs were skipped.


## Validated command archive foundation — 1 November 2026

Mobile [`0a78e8ed`](https://github.com/fajarxfce/hris-mobile/commit/0a78e8edb8d604da6d411a441d3fcdc34511ff65)
and Starter [`8afab2a`](https://github.com/fajarxfce/compose-fluent-starter/commit/8afab2a30c7313ecaa1d4b2c88359f45d942aa18)
are pushed; their 15 source/test/schema hashes match exactly.
[Evidence](measurements/command-archive-2026-11-01.json) records atomic retention
of the entire encrypted command plus bounded proof while removing pending work.
The raw store compares the complete observed record and current account/session;
equal-revision replacement cannot inherit another completion. Immutable archives
fence future same-ID insertion and retain key-loss protection after pending capacity
is released. SQLite schema 11 / IndexedDB version 10 preserve existing commands.
No feature infers completion or starts transport from this storage operation.

Ten new scenarios pass on actual SQLite and IndexedDB. Review strengthened the
initial revision-only version after its passing checks; both versions' sources,
results and artifacts remain distinct in the measurement. Corrected focused checks
pass in 118s (37 SQLite / 35 browser). Final Mobile full checks pass in 326s,
2,496 desktop / 165 Android host / 262 domain browser / 198 data browser / 35 database
browser; Starter full checks pass in 152s, 341 desktop / 35 database browser. All
have zero failures/errors/skips and zero lint errors. No phone or live change.

**Next: HRIS encrypted completion proof, archived read/replay, admitted local
retirement and restartable journal cleanup, then Chat UI and all P0–P8.**
See [continuation contract](https://github.com/fajarxfce/hris-mobile/blob/0a78e8edb8d604da6d411a441d3fcdc34511ff65/docs/collaboration-retirement.md).
Keep uncertain legacy work and validate original-history sender/content and aliases.
The completion and receipt stores have separate transactions; interrupted cleanup
must recognize immutable archives without recreating pending messages. All 54
product feature groups remain unaccepted. Current backend/live services are unchanged.

Additional hosted audits are now recorded. ACK recovery HRIS `41295813903` at
`9da09b6b` passes all 17 steps, 165 Android host cases and native run `c1aa858e`
(45.883s case / 95.540s suite), cleanup true, exactly three encrypted sends and
22/22/22 admissions. All 279 Mobile / 33 Platform hashes match its pinned fixture.
SDK reopening control `41293329837` at `69ba59de` passes all 28 steps, 464 desktop /
440 browser / 371 iOS simulator cases and Xcode host build, with 272 hashes matched.
These are predecessor-specific results, not evidence for the new archive policy.


## Active collaboration implementation — 1 November 2026

**ACK-before-journal recovery now passes actual Android/HRIS process death.**
[Evidence](measurements/collaboration-original-receipts-2026-11-01.json) binds
**279 Mobile / 33 Platform source hashes** to run
`39775a6b851040e19d1f4c97059025c8`: **41.453s case / 62.142s native suite /
91s Gradle**, cleanup true. The driver verifies three different processes across
staging and ACK deaths. The final process recovers the missing receipt from exact
original history, reopens the database and projects Sent once. The ACK checkpoint
contains no event ID or body. The server verifies exactly **three encrypted sends**
for the initial message, reply and separate ACK message; held-room sends remain zero.
Live admission is **22/22/22 device/conversation/room reads, zero failures**.
`ackBeforeJournalRecovered`, driver-owned `processRestartedAfterAck` and the two
admitted latest-history boundaries are mandatory evidence.

New staging persists trusted sender and canonical full-content hash before enqueue.
The native writer verifies that identity before mutation; Staging/Staged/Sent keep
it immutable. Explicit plain text replaces the SDK Markdown constructor: Markdown,
HTML, emoji and newlines remain literal content, with empty mentions and the logical
ID included in the hash. Old envelopes remain readable without invented identity.
`RecoverCollaborationMessageReceipts` is a local-only, owned/gated operation: up to
128 command candidates, at most 32 appended receipts, exact sender/logical ID/hash,
all event aliases and confirmed redactions. Missing originals stay pending; conflicts
reject the batch before append. Lost writes and late denial retain journal facts.
It never fetches history, starts SDK delivery or projects commands implicitly.

Focused domain/storage/host/DI checks pass in **132s**, followed by proof build and
quality in **55s**, Platform fixture/quality in **40s**, and **40 Python cases**.
Full root/browser/Android/Wasm/proof/lint passes in **235s**: **2,486 desktop /
165 Android host / 262 domain browser / 198 data browser**, zero failures/errors/
skips and zero lint errors. All attempts for this increment pass first time.
Dev APK **`1eb5a9a4`** is built, not installed; actual proof uses **`8f066ba0`**.
Emulator stopped; no phone or live change. Starter remains `36116b2`; this increment
is HRIS-specific and has no generic port.

**Next: safe command/journal retirement, Chat UI, then all P0–P8. None of the 54
groups is accepted.** Retirement must free the shared 128-command capacity while
preserving complete durable identity and retry deduplication; deleting Sent rows
alone would let an old logical UUID enqueue again. Account-owned raw storage must
atomically fence insertion against retained completion evidence. Command and receipt
stores currently have separate transactions. Preserve uncertain legacy work and all
aliases; validate interrupted retirement and lost responses before enabling cleanup.
Then integrate scheduling/UI, Apple/web owned adapters and interoperability, backup,
rich messages and all call/meeting phases. Physical iPhone testing is waived; Apple
implementation remains required. Continue the authorized long run, with atomic
commits pushed and this handoff updated; do not treat this checkpoint as completion.

Predecessor control report audit `41290902945` at `388d0534` reports success and
contains **451 desktop / 427 browser / 358 iOS simulator** passing cases; 266
immutable source hashes match. The API returned no job/step records and an empty
run log, so this audit does not independently claim an Xcode host build. See the
explicit limitations in the restart measurement's `hostedControlReports`.

### Previous SDK reopening checkpoint

**Coordinated SDK reopening now passes actual Android/HRIS recovery.**
[Evidence](measurements/collaboration-history-reopening-2026-11-01.json) records
run **`e395fab292fd4d088cae138b4aaebd27`**, **35.536s case / 56.375s native suite /
87s Gradle**, cleanup true. The fixture fills the 16-focus budget, proves that a
real queue writer blocks history-driven close, then restores the protected client
and verifies the same encryption keys/bootstrap, original message body, paused
sibling transaction and encrypted reference checkpoint. Live admission is
**17/17/17 device/conversation/room reads, zero failures**. The new
`historyRuntimeReopened` Boolean is mandatory across Android, Python and server.

`ReopenCollaborationHistory` admits the owner/device/direct room before preparation,
before restore and after restore. `Available` leaves a retained focus or spare
capacity untouched; `SetupRequired` never enrolls a new identity. A late denied,
replaced or cancelled restored lease closes. The native operation shares the
client-wide writer exclusion with message queues. An active/unfinished writer
stays owned; already-requested parent cleanup can finish. No original, reference,
intent or receipt is erased, and no history read, sync start or delivery is implicit.
The repository retains clients; application lifetime only closes obsolete owners.

Twelve new common-domain cases, four Android-host cases and one common focus case
pass. Initial focused attempts exposed two assertion type issues, a cleanup-wait
fixture assumption and a function-length check; all are preserved in the evidence.
Corrected focused checks pass in **50s**. The first actual run stopped at a bounded
close; the corrected fixture permits at most six explicit common requests, only
for `close_pending`. Its corrected proof build/quality passes in **51s**. This is
fixture-only retry; the product operation never loops. Full root/browser/Android/
Wasm/proof/lint passes in **231s**: **2,466 desktop / 161 Android host / 247 domain
browser / 193 data browser**, zero failures/errors/skips and zero lint errors.
Platform fixture/quality passes in **28s**; 35 Python cases pass. All **272 Mobile /
33 Platform source hashes** match. Dev APK **`b437c344`** is built, not installed;
actual proof uses **`e693f636`**. Dedicated emulator stopped; no live/phone change.

**Next: ACK-before-journal recovery, command retirement and Chat UI, then all
P0–P8. None of the 54 groups is accepted.** Original history already indexes exact
sender/logical ID and canonical full-content hashes. Do not infer ACK from a missing
native transaction or matching text alone. Current queue sending calls the SDK's
Markdown constructor, which may add formatted content: recovery needs a trusted
sender binding and the exact original payload identity before enqueue, not a body-
only hash. Inspect the pinned SDK content factory and preserve uncertain old work.
The SDK reopening scenario preserves stores but does not compare a newly synchronized
cursor after reopening; earlier process-death/429 cursor proof remains separate.
Apple/web owned adapters, interoperability, backup, rich messages and every call/
meeting phase remain. Physical iPhone testing is waived; Apple implementation is
required. Starter stays at `36116b2`; this HRIS-specific increment has no generic port.

Additional predecessor audits are recorded independently. Common acquisition control
**`41287684753`** at `0cb6509a` passes all 28 steps, **421 desktop / 397 browser /
342 iOS simulator** cases and Xcode build, with 256 immutable hashes matched.
Explicit restart HRIS **`41290902971`** at `388d0534` passes all 17 steps and run
`8f5ec0ad` (**33.788s case / 71.175s suite**), cleanup true, 11/11/11 admissions,
156 host cases and 266 Mobile/33 Platform hashes matched. Resolution control
`41289463827` was superseded while pending; it is not claimed as passed.

New predecessor audits are recorded separately. Traversal control **`41286066709`**
at `e992a053` passes all 28 steps, **407 desktop / 383 browser / 328 iOS simulator**
cases and actual Xcode build, with 253 immutable hashes matched. Offline resolution
HRIS **`41289463686`** at `61c6b516` passes 17 steps, 156 host cases and run
`21032404` (**50.626s case / 91.170s suite**), cleanup true, strict reconciliation
flag and 262/33 hashes matched. Resolution protocol **`41289463684`** reports
success. Acquisition control `41287684753` is running; resolution control
`41289463827` was pending at the last check. Audit terminal artifacts and this
increment's own CI after push; earlier proofs do not validate later code.

Mobile’s next commit must pin this Platform fixture commit in its HRIS workflow.

No live/schema/phone change. Atomic commits and push after every commit remain
authorized. Continue after this checkpoint; the user requested the full plan.

[Earlier November detail](collaboration/implementation-history-2026-11-01.md)

## Previous collaboration checkpoint — 31 October 2026

The user approved [the full collaboration plan](collaboration/prd.md), atomic
commits, a push after every commit and continued P0–P8 implementation. All three
repositories use `feat/communications` (Platform base `1363abee`, Mobile base
`7e12251a`, Starter base `44d164c`). [Delivery](collaboration/delivery.json) tracks
54 feature groups; **none is accepted yet**. Infrastructure checkpoints do not
complete chat/calls/meetings. Continue implementation without treating these
checks as the end of the task.

Read [architecture](collaboration/architecture.md), [threat model](collaboration/threat-model.md),
[admission](collaboration/admission.md), [gateway contracts](collaboration/gateway-sessions.md)
and the delivery evidence. Earlier detailed checkpoints remain in
[implementation history](collaboration/implementation-history-2026-10-31.md) and Git.

Current provider/client evidence:

- **Mobile protected runtimes now own room/timeline observers at `0ef8018c`.**
  [Cross-repository contract](collaboration/mobile-setup.md) and
  [measurement](measurements/collaboration-owned-timeline-2026-10-31.json) cover
  ordered primitive projections, partial/cancelled acquisition, sibling disposal
  and retained failed cleanup before database unlock. Fifteen new common cases;
  root/browser/Android/Wasm/lint passes in 245s (2,160 desktop, 64 data browser,
  zero lint errors). Actual SDK run `5aea81cc` passes in 34.340s, cleanup complete,
  all 15 Platform/120 Mobile hashes unchanged. One provider-busy key upload 503
  precedes successful exchange. Conversation command control `41248280288` also
  passes all 26 steps (143 desktop, 119 browser, 119 iOS simulator; all 26 source
  hashes checked). Emulator stopped, no live/schema/phone change. **Continue
  admitted content ports, app entry, product history windows, offline projections,
  recovery/reconciliation, chat UI and all P0–P8. No group is accepted.**

- **Mobile durable conversation control is pushed at `39217be2`.**
  [Cross-repository checkpoint](collaboration/mobile-setup.md) records encrypted
  per-peer/room commands, uncertainty before HTTP, immutable retries and exact
  receipt reconciliation. Both setup and conversation preserve known server denial
  across failed transport retries. Final root/browser/Android/lint passes in 4m:
  2,145 desktop cases, 70 domain/49 data browser, zero lint errors. Mobile CI now
  lets running proofs finish; `fbb2f3ae` provider run `41246770541` passes both modes
  with all steps and source hashes verified. Its control/HRIS runs `41246770522`
  and `41246770543` also pass all jobs/steps: 119 desktop, 103 browser, 103 iOS
  simulator cases; actual SDK case 70.969s, cleanup complete, all 94 Mobile/15
  Platform hashes verified (Mobile evidence `a07c90e1`). Apple native chat remains
  pending. Starter AppAuth hosted verification
  also passes (`9740cb5`/`41245287269`), recorded at `36116b2`. No product group,
  live/schema/phone change. **Continue room/timeline ownership, chat UI, offline
  projections/recovery and all P0–P8.**

- **Mobile SDK synchronization now follows protected runtime ownership.**
  Mobile `fbb2f3ae` replaces the HRIS fixture's separate sync producer
  with lease-owned classic sync and verifies duplicate starts, cancellation/late
  acquisition and explicit failed-cleanup retry. [Evidence](measurements/collaboration-owned-sync-2026-10-31.json)
  records 15 Platform/94 Mobile hashes, APK `e5c07ded`, actual Android case `54ab8f5a`
  passing in 35.750s, one API and 32 driver cases, cleanup complete. Root/Wasm/
  Android/lint passes in 192s with 2,121 desktop cases; data browser has 45. One
  provider-busy key upload is retained before successful SDK completion. Emulator
  stopped, no live/schema/phone changes. Mobile `50964794` adds scoped conversation
  control reads; durable mutations, product room/timeline ownership and chat UI
  remain next. **Continue all P0–P8; no group is accepted.**

- **Mobile application runtime ownership is pushed at `9f83dcdc`.**
  [Cross-repository checkpoint](collaboration/mobile-setup.md) links admitted
  install/restore, application DI and Android process lifecycle. Final root/Wasm/
  Android/lint passed in 70s: 2,093 desktop cases, 51 domain browser cases, zero
  lint errors. Starter `9740cb5` independently ports the manifest AppAuth dependency
  and CI lint guard (331 desktop cases, build/lint passed). Corrected provider CI
  `41242901470` passes both modes/all steps against `e0dbb567`, with source hashes
  and cleanup verified. Mobile control/HRIS CI and Starter verify are still running
  at this checkpoint. **Continue conversation adapters/UI, SDK child ownership,
  bootstrap recovery, offline reconciliation and all P0–P8.** No product group,
  live rollout or physical phone acceptance is implied.

- **Provider fixture IPC race fixed and classic Android/web regression passes.**
  Mobile `70bfbd21` run `41240634061` passed native but failed classic at
  `ProofOfflineInvite.kt:28`: the reader saw the final signal name before its
  producer finished writing. The producer now writes a private temporary file,
  then publishes by rename. [Evidence](measurements/collaboration-offline-signal-2026-10-31.json)
  retains both predecessor results/hashes and actual corrected classic proof
  `bc5a0106`: 35.498s, two browser cases, zero unexpected/flaky/skipped, cleanup true.
  Node syntax check passes. Mobile protocol CI must pin this Platform correction.
  No product behavior, timeouts, live service or phone changes.

- **An actual Android process death now preserves the pending SDK transaction.**
  [Evidence](measurements/collaboration-sdk-process-death-2026-10-31.json) binds
  15 Platform/71 Mobile sources to APK `a14d6bf1`. The owned proof process is
  force-stopped after durable enqueue, then a different PID restores both protected
  identities and the original transaction. Gateway observation confirms zero
  encrypted sends before explicit release; both directions decrypt and server
  reads confirm ciphertext. Actual SDK case 33.512s; full fixture/quality run 62s,
  four API cases and 32 Python cases pass. Mobile root/debug/lint/browser passes
  in 66s, 2,063 desktop/24 data browser cases. Cleanup completed and the owned
  emulator was stopped. **Next: admitted bootstrap installation/recovery, app
  session ownership, logical-message reconciliation and DI/UI; continue all P0–P8.**
  This proves SDK queue persistence, not the full product outbox/notification or
  Apple runtime. No live/schema/phone/Starter change and no product group accepted.

- **Protected Android store reopening through HRIS now passes.**
  [Contract](collaboration/native-hris-sdk.md) and
  [evidence](measurements/collaboration-protected-native-store-2026-10-31.json)
  retain 15 Platform/70 Mobile hashes, APK `18b072bc`, actual SDK 22.732s and
  successful cleanup. Real Keystore records/private SDK stores reject competing
  opens, missing records and wrong passphrases; restoring the original record
  preserves both device keys before bidirectional encrypted messages. A discovered
  gateway bug rejected equal IDs across one-time/fallback maps; those independent
  maps now preserve separate values while verifying every signature and fallback
  marker. Two failed runs (74s/46s) and bounded diagnostic counts remain recorded.
  Full 454s regression passes 37 domain, 201 data, 39 API and quality; 27 driver
  cases pass. Mobile root/debug/lint/browser passes in 78s: 2,063 desktop/24 data
  browser cases. SDK store shutdown now drains the native database pools before
  unlock, with cancellation/retry tests. The newer checkpoint above adds abandoned
  close recovery and actual Android queue process death. No live,
  schema, phone or Starter change; no product group accepted. Mobile's matching
  proof commit/CI pin follows this Platform commit.

- **Mobile durable setup commands are pushed at `fb1b1c13`.**
  [Cross-repository checkpoint](collaboration/mobile-setup.md) links the exact
  implementation, 39 source hashes, contract and SDK lifecycle audit. Native
  registration/status/retry/bootstrap transport and encrypted command ownership
  pass root/assembly/browser checks in 62s: 2,040 desktop cases; collaboration
  domain 23/data 15, browser domain 23/data 7. Immutable operations survive lost
  responses and process/store reopening; late scope changes cannot publish results.
  Gateway tokens remain encrypted pending SDK installation; only observed confirmed
  registration/new-job receipts retire. No app DI/routes or SDK message durability
  yet. Hosted Apple/control checks remain pending after fixing SDK package selection
  and the four local Wasm lock entries. **Continue protected SDK store/session,
  queue restart, bootstrap recovery, UI and all P0–P8.** No product group accepted,
  schema/live/phone/Starter change. Mobile also pushed deterministic October expense
  fixture clock `d58ee287`; production date selection remains unchanged.

- **Actual Android SDK through HRIS passes**: two fictional employees, separate
  encrypted stores, keys/claim/to-device, bidirectional Megolm messages and classic
  sync all use the real gateway. Server event reads verify ciphertext; decrypted
  client content retains logical IDs. [Contract](collaboration/native-hris-sdk.md)
  and [measurement](measurements/collaboration-native-hris-sdk-2026-10-31.json)
  retain 10 Platform/12 Mobile source hashes and APK `3b10b319`. Final 83s run:
  19 API cases, format/architecture passed; all 27 Python driver cases passed.
  Mobile root/debug/lint passed in 69s. Four earlier timeout failures exposed
  the pinned SDK's unprefixed `use_state_after`; strict boolean preference now
  permits the advertised v1.12 state-before response, verified by the SDK.
  Device activation/binding publication remain explicit SQL fixtures; provider
  room creation and HTTP/crypto are real. Cleanup passed. No live/phone/Starter
  change. Send commit c3b0955d CI 41229637604 passed both jobs/all 23 steps and
  all 18 source hashes were verified. **Continue product session/store ownership,
  durable SDK queue restart/logical reconciliation, UI and the full P0–P8 plan.**
  Matching Mobile `538a1827` is pushed. Hosted HRIS SDK run 41231913617 passed
  all steps: one real SDK case in 63.437s, APK `9900b041`, cleanup true; all
  twelve Mobile hashes and ten Platform hashes match their commits. Platform
  control 41231798310 passed both jobs/all steps. Results and trace are retained.
  Mobile's product setup adapters and durable commands are now pushed and validated
  as described above. This accepts no product feature.

- Native DIRECT encrypted sends now use exact owned-room scope, confirmed sender
  publication and current eligible peer devices, preserving offline short-token
  expiry behavior. Key preflight and send each revalidate room/recipient/admission;
  late denial withholds the result. Actual Synapse retry/same-device bearer rotation
  returns one event and accepts the 60,000-character ciphertext bound. [Contract](collaboration/room-messages.md)
  and [evidence](measurements/collaboration-room-messages-2026-10-31.json) retain
  18 source hashes and 52s focused coverage. Full 641s regression hit two existing
  provider/container lifecycle timeouts while an owned Mobile build ran under
  observed memory pressure. After stopping only its completed idle daemon, both
  failed classes passed serially in 68s without source changes. Combined coverage:
  36 domain, 201 data, 160 selected API, 112 worker (one opt-in skip), quality and
  both DI graphs. No schema, live or phone update. History fabaa9a5 hosted
  41227795949 passed both jobs/all 23 steps, all 24 source hashes verified.
  **Continue actual pinned SDK through HRIS, then durable outbox/UI and P0–P8.**
  Mobile has four in-progress proof files (`HrisNativeMatrixProbe`, `HrisProofClient`,
  `HrisProofMessages`, instrumentation scenario `hris`): debug assembly/lint passed
  in 274s; actual gateway execution and root checks are still pending. They are
  uncommitted until the owned HRIS fixture/driver is connected and tested. Use only
  the dedicated emulator. Run resource-heavy builds serially on this machine.

- Native DIRECT history now exposes fixed messages/context/event reads with exact
  retained room ownership and current admission before/after I/O. Sync prev_batch
  and page/context positions are AES-GCM bound to company/account/device/server/
  room, separate from sync/member positions. Actual Synapse 25-event pagination
  terminates without duplicates; context works both directions. Full checks pass
  in 426s: 34 domain, 194 data, 159 selected API, 112 worker (one opt-in skip),
  format/architecture and both DI graphs. [Contract](collaboration/room-history.md)
  and [measurement](measurements/collaboration-room-history-2026-10-31.json) retain
  the initial fixture burst-429 failure, corrected 59s checks and 24 source hashes.
  Encrypted sends are implemented above. Continue actual pinned SDK through HRIS,
  durable outbox and product UI, then the full P0–P8 plan. No migration,
  live service, phone, Mobile or Starter change occurred. The prior room-access
  commit b82888b6 hosted 41225780266 passed both jobs/all 23 steps; all 23 matching
  source hashes and terminal metadata are retained in its measurement.

- Native Matrix discovery and DIRECT join/state/member reads now use exact owned
  room lookup through existing RLS. `members?at=` opens only the calling device's
  sealed sync position; actual Synapse preserves historical invite state after a
  later join. Read/join use control capacity while sync owns its separate pool.
  Preparation rechecks admission even after a failed resource lookup. Full checks
  passed in 379s: 34 domain, 187 data, 158 selected API, 112 worker (one existing
  opt-in skip), format/architecture and both DI contexts. Actual HRIS worker
  publication → Synapse → gateway join/state/sync/member reads passes; all HTTP
  routes, strict framing, foreign/unpublished room denial and late revocation are
  covered. [Contract](collaboration/room-access.md) and
  [evidence](measurements/collaboration-room-access-2026-10-31.json) retain source
  hashes and the corrected duplicate-helper compilation failure. Pagination and
  context/event reads are implemented above. Continue encrypted sends, the actual
  pinned SDK through HRIS, durable outbox and UI. Client filters,
  additional fixed discovery, browser admission, group/context scope and the rest
  of the full plan remain. No schema migration, live update or phone action.
  Sync `43391884` hosted `41223350867` passed all jobs/steps; its 18 exact source
  hashes and raw terminal metadata are retained in the sync-gateway measurement.

- Native `GET /_matrix/client/v3/sync` now has current admission and immutable
  DIRECT scope fences, owner-bound encrypted positions, initial reacquisition on
  scope growth and fixed own-user provider filters. Every room bucket is filtered
  in HRIS; actual immutable creation metadata is hydrated only when absent from
  state/timeline. Separate finite long-poll slots do not consume control capacity.
  [Contract](collaboration/sync-gateway.md) and
  [evidence](measurements/collaboration-sync-gateway-2026-10-31.json) retain exact
  bounds, failures/corrections and source hashes. Full 352s coverage plus the 34s
  test-only correction passes 34 domain, 181 data, 157 selected API and 112 worker
  cases (one existing opt-in skip), format/architecture and both DI contexts.
  The full run's two obsolete assertions are retained honestly: sync is now an
  authenticated route; actual creation may be in state or timeline. Cursor survival
  after ordinary gateway refresh and immutable-only hydration were added.
  Pinned Synapse and actual HRIS-to-provider sync pass; this is not native SDK/UI,
  capacity or durable outbox acceptance. **Continue with fixed SDK discovery,
  room join/history/encrypted send, then actual HRIS SDK integration and outbox.**
  Browser/native client filters, immediate servlet-disconnect observation, scoped
  pagination and provider rollback recovery remain explicit gates. No migration
  or live/phone update occurred. Scope proof `54a3c974` CI `41220888684` and cursor
  foundation `2c63c5bf` CI `41221573663` both passed all jobs/steps; exact source
  hashes and terminal metadata are retained in their measurements.

- Native DIRECT sync foundations now include one bounded forced-RLS room projection
  and an AES-GCM cursor bound to company/account/device/Matrix server. Peer
  offboarding does not delete retained history. Scope growth changes its digest;
  invalid, ambiguous or oversized scope is rejected without truncation. Focused
  domain/storage/crypto plus quality checks passed in 22s. [Contract](collaboration/sync-gateway.md)
  and [evidence](measurements/collaboration-sync-foundation-2026-10-31.json) retain
  bounds and key-rotation/reset behavior. The admitted sync implementation above
  now consumes this foundation; native SDK and durable outbox remain.
  To-device `199d541f` hosted `41219599675` passed every job/step; its 17 source
  hashes and raw terminal jobs are retained in the to-device measurement.

- Actual classic sync scope expansion now passes for joined and invited rooms.
  Provider room filters still return out-of-filter invitations; full_state with
  since does not replay old invites. Initial reacquisition restores invitations,
  while the invited-history creator needs an explicit state read for m.room.create
  (even repeated initial sync omits it). [Contract](collaboration/sync-scope-recovery.md)
  and [evidence](measurements/collaboration-sync-scope-2026-10-31.json) retain the
  corrected assumptions, final 14s case, format/architecture and exact source.
  The admitted sync implementation above now preserves state/timeline ordering
  and filters every room bucket. The provider experiment alone does not establish
  native SDK acceptance.

- Both native SyncService and classic Client.syncV2 now pass the same actual
  Android/browser proof with one APK `d835e1c1`: two cases each, 18.814s native /
  19.219s classic; owned runs 33.649s / 38.121s, cleanup true. Classic omitted
  optional room member summary counts; exact complete SDK membership now gates
  offline sending in both modes. [Decision](collaboration/sync-mode.md) and
  [evidence](measurements/collaboration-dual-sync-2026-10-31.json) retain failures,
  18 Python checks, source hashes, final 23s root/debug/lint (2,002 desktop cases,
  zero lint errors). Use classic for the first scoped HRIS Android/web transport;
  native list/subscription/extension authorization remains a separate gate. This
  fixture targets the provider directly; actual HRIS SDK integration and product
  acceptance remain. Exact Mobile
  `b061d390` hosted `41212817421` passed every job/step in both modes: classic
  31.659s/72.695s and native 34.002s/81.267s, both cleanup true. Platform `d425e6d8`
  browser `41212746123` also passed every step; all artifacts and matching source
  hashes are retained in the dual-sync measurement. Mobile `9ecbd004` records them.

- Actual Matrix/LiveKit browser E2EE exchange/media and wrong-key rejection pass.
  The experiment's custom encrypted media-key event is **not MatrixRTC acceptance**.
- Android SDK 26.10.23 ↔ browser encrypted text, standard credential refresh and
  unchanged signing key pass. The latest extra-content/paused SDK queue proof
  preserves `org.hris.message_id` inside encryption and the SDK's local transaction
  through its server receipt. Local two-scenario run: 29.090s with cleanup.
  Mobile `e8a5ceb3` passed every hosted step in [41198048864](https://github.com/fajarxfce/hris-mobile/actions/runs/41198048864),
  two scenarios in 84.382s; `6bc502a0` records source/APK hashes. Driver is Platform
  `d0731afe`. Use asynchronous client-level queue control: synchronous room queue
  control failed in this binding. Root/debug/lint checks pass with 2,002 desktop
  cases and no lint errors. [Queue evidence](measurements/collaboration-native-logical-queue-2026-10-31.json)
  retains corrected failures; durable process restart/recipient coalescing remain.
- Swift SDK 26.10.27 native encrypted-store reopening/wrong-key checks and iOS
  compilation passed; actual Apple provider interoperability/native media remain
  unverified. Physical iPhone acceptance is waived, not inferred from compilation.
- Pinned Synapse 1.164.1 deduplicates a transaction by room/user/device only while
  its retained mapping exists. Actual same-device bearer replacement deduplicates;
  fixture aging/cleanup/restart permits duplicates. A peer receives no transaction
  ID. [Retention evidence](collaboration/provider-transaction-retention.md) requires
  durable encrypted logical IDs, scoped recipient coalescing, event aliases and
  tombstones. Never assume unlimited provider deduplication.

- Actual pinned Synapse room-version-12 provisioning checks now pass: unknown local
  peer invitation survives first login, alias replay/restart resolves one room, and
  peer state/invite escalation is denied. An invalid initial power state leaves a
  partial room/alias without encryption; verified creator/marker plus state repair
  completes it. [Evidence](measurements/collaboration-room-interop-2026-10-31.json)
  records two cases (36s Gradle), format/architecture and owned cleanup. No HRIS
  binding/publication or SDK E2EE is implied by this control proof.

- The latest browser Rust-crypto proof now sends the first message while the enrolled
  invitee is offline, then decrypts after joining with the sender offline. **Direct
  rooms need invited history**; joined excludes invitees from encryption targets.
  Wait for SDK encryption/member state, load invitation history, and use enabled
  timelineSupport/event-timeline acquisition after restart. Corrected initial lookup
  and media-event acquisition failures remain in [adapter evidence](measurements/collaboration-room-adapter-2026-10-31.json).
  Full final browser case passed in 6.780s (17.755s owned run, cleanup true), including
  prior restart/media/wrong-key assertions. Android-sender evidence follows below; native recipient-before-join and Swift remain.

- Android SDK now creates an encrypted room with invited history, sends through its
  paused/resumed queue while the enrolled browser invitee is offline, then closes.
  The browser joins and decrypts after the Android process is stopped. Explicit
  room version 12, encrypted logical ID, standard refresh/signing-key stability and
  prior web/media assertions pass. [Native invitation evidence](measurements/collaboration-native-invited-2026-10-31.json)
  retains two scenarios in 18.486s (33.494s owned run), cleanup true; full Mobile
  root/debug/lint checks passed in 48s, 2,002 desktop cases and zero lint errors.
  Native recipient-before-join and never-enrolled-device delivery remain separate.
  Emulator stopped with data retained; no phone action. Mobile `e6dcb5ea` pins
  driver `54eb3a66`; every job/step passed in [41204075013](https://github.com/fajarxfce/hris-mobile/actions/runs/41204075013),
  both scenarios in 34.359s (82.746s owned run), cleanup true and matching driver
  hashes. Mobile `4f8233c2` retains its source-matched hosted APK evidence. The
  driver browser workflow [41204052403](https://github.com/fajarxfce/hris-platform/actions/runs/41204052403) also passed every job/step.

- The [SDK protocol surface](collaboration/sdk-protocol-surface.md) now has actual
  method/static-servlet/count evidence, without identifiers or payloads. Browser
  baseline passed in 15.512s; Android/browser passed in 39.009s with cleanup and
  unchanged source-matching APK `d3662eb9`. Fifteen parser/ownership checks pass.
  The native fixture explicitly uses NATIVE SyncService; eighteen native Sliding
  Sync requests occur only in the combined run. Pinned Synapse implements MSC4186.
  Classic Kotlin syncV2 exists in the binding but is unproved. Do not open generic
  provider routes from these aggregate counts: negative/setup requests are included.
  Scope native lists/subscriptions/extensions and classic cursor/filter behavior;
  withhold unverified rooms. Emulator is stopped and phone untouched. Exact hosted
  browser `41207148334` and Mobile `41207178159` passed every job/step, source hashes
  match, and sanitized artifacts retain cleanup: 8.995s/32.972s browser,
  33.604s/77.491s native combined (scenario/owned duration). Mobile head `7b57865b`
  pins Platform `047897f9`; hosted APK hash is retained separately from the local build.

Current backend implementation:

- Fixed native `PUT /_matrix/client/v3/sendToDevice/m.room.encrypted/{txn}`
  now validates confirmed sender publication, pinned identities and exact current
  self/direct device targets. It retains opaque ciphertext, optional Olm message
  ID and transaction ID; missing/inaccessible targets reject the whole request.
  Every provider exchange fences current scope outside SQL. [Contract](collaboration/to-device-envelopes.md)
  and [evidence](measurements/collaboration-to-device-2026-10-31.json) retain final
  5m37s checks: 31 domain, 167 data, 155 API, 112 worker (one existing skip),
  format/architecture and both DI graphs. Focused 39s passed after a corrected
  Int/Long reader compilation error. Actual HRIS-to-Synapse and offline queue
  routing pass; opaque fixture bytes do not prove Olm decryption. Same-device
  token rotation deduplicates, but provider restart re-enqueues the same envelope.
  Its process-local cache retains entries 30–60 minutes, unlike room transaction
  storage. Prove SDK replay/store recovery through complete HRIS integration.
  Next implement scoped classic sync/cursor state reacquisition and room content.
  Pinned Synapse full_state bypasses long polling: never force it on every poll.
  First verify previously filtered joined/invited rooms when scope expands after
  an old provider cursor. Sync needs its own bounded long-poll owner and current
  response admission, independent of short-control permits.

- Native `POST /_matrix/client/v3/keys/claim` now performs pinned device-key
  preflight, then validates signed one-time/fallback keys for explicit eligible
  self/direct devices. Every exchange fences current caller and recipient scope;
  unavailable targets return empty entries without consumption. No automatic
  replay or logout compensates for a consumed/lost response. [Contract](collaboration/device-key-claim.md)
  and [evidence](measurements/collaboration-device-key-claim-2026-10-31.json) retain
  actual Synapse distinct-key exhaustion/fallback/restart and HRIS-to-provider
  checks. Combined coverage: 28 domain, 160 data, 153 API, 112 worker (one skip),
  format/architecture. The 5m19s full run had one existing token-expiry timing
  fixture failure; its corrected complete case passed in 20s. Production source
  stayed unchanged after that full run. Earlier 67s/66s focused runs retain the
  corrected offline fixture column typo. Exact `42dda378` hosted `41218262873`
  passed every job/step; 17 source hashes match the retained measurement.
  Encrypted to-device is implemented above;
  continue scoped classic sync/content and durable product UI/outbox.

- Native `POST /_matrix/client/v3/keys/query` now uses the recipient projection,
  current caller/self/direct authority and immutable self-signed fingerprints.
  Unknown/unavailable peers return empty entries without blocking current devices
  in a batch; provider requests always contain explicit devices. Caller admission
  and the selected set are fenced before/after I/O, with no SQL held across HTTP.
  [Contract](collaboration/device-key-query.md) and
  [evidence](measurements/collaboration-device-key-query-2026-10-31.json) retain
  final 5m09s checks: 26 domain, 152 data, 151 API, 112 worker (one existing skip),
  format/architecture and both DI contexts, plus corrected fixture failures.
  Transport has a one-MiB bound for bulk keys, other responses stay 64 KiB.
  Actual Synapse readback/restart and HTTP framing/auth denial pass; this is not
  yet complete SDK content traffic. Signed key claim is now implemented above;
  continue scoped classic sync/content and durable product UI/outbox. Exact
  `a33e5a63` hosted `41217138590` passed every job/step; 18 source hashes match
  the retained evidence.

- Schema **113** adds a read-only public recipient view owned by a restricted
  non-login role. Application-role private device/binding/key RLS stays unchanged.
  Only self/current direct peers with active company/account/membership/chat
  permission and confirmed keys are eligible. Short access expiry and old MFA
  preserve offline delivery eligibility; hard parent expiry/revocation, security
  changes, device revocation and offboarding remove it. Parent cleanup retains
  owned device/key facts. The view returns five fields and no labels/credentials;
  its storage port has a 100-account/500-result bound and now serves typed key query.
  [Contract](collaboration/recipient-devices.md) and
  [evidence](measurements/collaboration-recipient-devices-2026-10-31.json) retain
  seven actual storage cases, 112-to-113 upgrade, pg_dump/restore ownership/RLS,
  final 6m43s checks (30 database, 140 collaboration data, 148 API, 112 worker with
  one existing skip), domain suites, format/architecture and 23 recovery checks.
  Initial 53s test corrected its expected Result.Failed mutation denial; final
  focused 28s passes. Exact `b3b9b9a1` hosted `41213850974` passed all jobs/steps;
  evidence is retained. Typed key query now adds current admission and
  retained-fingerprint checks before/after every provider exchange.

- The exact `f104f097` room-control workflow [41206499276](https://github.com/fajarxfce/hris-platform/actions/runs/41206499276) passed every job/step, including both runtime graphs and real storage/provider checks; retained in the room-provisioning measurement.

- Schemas 104–108 retain company/account/device/native-parent identity, independent
  COLLABORATION availability, authenticated encrypted internal provider credentials
  with expiry/generation, and an atomic provisioning queue/pointer. Five distinct
  live native parents count across companies. Ordinary access expiry retains owned
  device/history facts. `collaboration.chat` is explicit; never rewrite historical
  role/member snapshots to grant it.
- Native registration `POST /api/v1/companies/{companyId}/collaboration/native-device`
  requires native transport, client headers, idempotency and current HRIS authority.
  GET exposes safe status; POST `/retry` requires observed device/pointer versions
  and terminal failed/cancelled pending work, with a one-minute bound. Activation
  locks/prepares, releases SQL for private Matrix login, then fences current
  admission, job lease, pointer and credential before atomic publication/completion.
  Actual worker/PostgreSQL/Synapse outage/recovery and owned shutdown are verified.
- Schema 109 stores one public gateway head/device, opaque token hashes and one
  bounded encrypted response cache. POST native `/bootstrap` requires observed
  versions/idempotency; POST `/_matrix/client/v3/refresh` uses only the standard
  JSON refresh token. Five-minute access, 30-second replay, ten-second rotation
  minimum; native access/MFA/current policy remain required. Worker cleanup clears
  at most 100 expired caches every five seconds, with cancellation/rollback and
  retained generation. Storage, concurrent replay, RLS and real key-ring restore pass.
- Fixed GET `/_matrix/client/v3/account/whoami` now checks current native/company/
  permission/MFA/build/device/head authority before and after private provider I/O.
  It exposes validated public identity only. Internal renewal fences generation,
  head and admission; competing candidates retain one winner without logout.
  SQL guards are not held across HTTP. Provider failure also rechecks current
  denial. Ordinary native refresh invalidates a stale observation temporarily;
  a subsequent admitted request can use the same gateway. Transport is fixed-origin,
  no-redirect, at most five seconds/64 KiB, with 32 immediate permits and cancellation.
  The own-device upload below extends this boundary; peer key/sync/room, login,
  logout, admin and federation remain denied. This is not a complete SDK content gateway.

- Native company participant discovery now supports bounded literal name search and
  UUID pagination. SQL excludes the caller before limiting, returns account/name
  only, and requires current active chat-enabled membership. Ordered guards and
  completion checks cover native parent, MFA, build and scheduled policy expiry.
  No Matrix login or device allocation is needed. [Contract](collaboration/participants.md)
  remains a selection hint: conversation commands must authorize selected peers again.

- Schema 110 and native direct-conversation open/list/detail now retain one immutable
  pair per company. Current peer eligibility is checked even on receipt replay;
  forced RLS hides unrelated accounts/companies. Operation/pair/access guards and
  final assurance make receipt, audit and outbox atomic. PostgreSQL unsigned pair
  order differs from the identity module's signed UUID account-lock order; both
  are tested, including an overlapping administration writer. Metadata survives
  peer offboarding. [Contract](collaboration/direct-conversations.md) is durable
  intent only: it does not provision a Matrix room or authorize content.

- Internal typed Matrix room ports now resolve/create/inspect, set one of five
  initial states and invite the peer. They share the identity HTTP owner/32 permits,
  five-second/64-KiB bounds and cancellation. URI segments are encoded, state arrays
  are bounded/unique, and a pure policy refuses foreign creator/marker/membership,
  additional creators, tombstones or incompatible existing security state. Only
  missing initial state is repairable. Actual Synapse exercises the production
  adapter; the room worker below now publishes a verified HRIS binding. Public
  Matrix content routes remain closed.

- Schema 111 now stores one retained direct-room provisioning pointer and a separate
  immutable verified room binding. Pair-only RLS reads survive native-parent cleanup;
  creator-only retries require terminal old jobs and CAS, retain creator, and may
  move to a new active device of that account. Publication rejects stale job/version
  and duplicate provider room identity. Its command/status and worker follow below.
  [Storage contract](collaboration/direct-room-storage.md) explains lock order and
  why peer creator takeover is forbidden. [Storage evidence](measurements/collaboration-direct-room-storage-2026-10-31.json)
  records six real PostgreSQL cases, 110-to-111 upgrade and actual dump/restore,
  concurrent retries, interruption and transaction rollback. Full checks passed
  in 7m05s: 30 database, two job-domain, 17 collaboration-domain, 102 data, 144
  selected API and 111 worker cases (one unchanged opt-in retention skip), plus
  format/architecture, 23 Python recovery cases and 14 dashboard job cases/typecheck.
  Initial test compilation failed on flatMap type arguments; corrected focused
  six-case run passed in 54s. Recovery ceiling is now 111; no new envelope format.

- Native `POST/GET .../direct-conversations/{conversationId}/room` and the
  COLLABORATION_DIRECT_PROVISION worker now enqueue/reconcile one verified room.
  Initial/idempotent requests converge, terminal retries retain creator and may use
  a new current device, and metadata distinguishes pending/ready/failure. Every
  provider exchange runs outside SQL and repeats current creator/peer admission;
  lost responses, partial state, refresh, revocation and interruption are fenced.
  Binding, job completion and journal commit atomically. No provider logout or
  public key/sync/content route is introduced. [Contract](collaboration/direct-room-provisioning.md)
  and [evidence](measurements/collaboration-direct-room-provisioning-2026-10-31.json)
  retain twelve focused data cases, eight HTTP cases and six actual-worker cases,
  including first setup and paused-provider recovery. Full checks passed in 5m12s:
  18 domain, 114 data, 145 selected API and 112 worker cases (one existing skip),
  formatting and architecture. Exact hosted control `41206499276` passed every job/step.
  Corrected receipt assertions and callback syntax remain recorded. The expanded
  worker fixture hit Synapse's per-address five-login bucket (0.003/s); its bounded
  address capacity is now 32 at 5/s, with account limits intact. Production capacity
  needs separate configuration/load proof. READY does not imply recipient keys.

- Schema 112 now reserves an immutable own-device public-key fingerprint before
  provider uploads; only version zero to one publication is allowed. RLS restricts
  reads/writes to company/account and triggers retain identity even through owner
  mutation attempts, ordinary parent cleanup and device revocation. The repository
  owns an explicit guard after native/device/gateway guards, without provider I/O.
  [Contract](collaboration/device-key-storage.md) and [evidence](measurements/collaboration-device-key-storage-2026-10-31.json)
  retain five real PostgreSQL cases, 111-to-112 upgrade and actual dump/restore.
  Focused checks passed in 59s; full checks passed in 7m45s: 30 database, 18 domain,
  119 collaboration data, 145 selected API and 112 worker cases (one existing skip),
  format/architecture and 23 Python recovery cases. No public route or DI change;
  the gateway must still validate signed keys, reject replacement identities,
  check current authority around provider I/O and confirm readback (implemented below).
  Exact `966dee38` hosted [41208833880](https://github.com/fajarxfce/hris-platform/actions/runs/41208833880)
  passed every job/step. No product acceptance, rollout or Starter port.

- Native Matrix `POST /_matrix/client/v3/keys/upload` now uses the opaque gateway
  bearer and typed signed-key payload. `AccessNativeCollaborationMatrixDevice`
  owns WHOAMI and key upload through a private admission/renewal runner, without
  use-case chaining or generic proxy callbacks. Own user/device, exact public-key
  shape, Ed25519 canonical-JSON signatures, bounded collections and retained
  fingerprint are checked. Preflight refuses existing replacement keys; query,
  upload and readback run outside SQL with current authority before/after even
  errors. Version-one publication/journal is fenced and atomic; lost responses
  retain version zero for same-key retry. Counts-only requests create no identity.
  Input is 64 KiB, 100 one-time keys, one fallback key; missing/false fallback stay
  distinct signed objects. No private participant key enters server storage.
  [Contract](collaboration/device-key-upload.md) and [measurement](measurements/collaboration-device-key-upload-2026-10-31.json)
  retain independent OpenSSL signature vectors, real Synapse repeat/rotation/restart,
  and actual HRIS publication. Synapse accepts device-key replacement through the
  same bearer: the HRIS immutable reservation is required. Full checks passed in
  4m44s: 23 domain, 131 data, 148 selected API and all 112 worker cases (one existing
  skip). Two additional readback/concurrent-same-key cases passed in 41s: 133 distinct
  data cases total. Earlier focused gateway 75s/HTTP 31s, format/architecture pass;
  the corrected JsonNode mapping compilation error remains recorded. The shared DI
  graph was exercised in both runtimes. Exact `05685bf2` hosted control
  [41210683028](https://github.com/fajarxfce/hris-platform/actions/runs/41210683028)
  passed every job and step; retained in the measurement. No product rollout. Query and claim are implemented above;
  encrypted to-device is implemented above; room/history and classic/native sync remain closed.

[Identity evidence](measurements/collaboration-gateway-identity-2026-10-31.json)
records 148 passing API cases, 77 data cases, fourteen domain cases and the full
111-case worker suite (110 pass, one unchanged opt-in retention skip). Formatting
and architecture pass. The final successful selection took 3m39s, with the
unchanged domain/data/worker results retained incrementally. Corrected failures
include a fixture missing its explicit Synapse start and two legacy eight-module
assertions; catalog tests now verify all nine names and run in collaboration CI.
The identity increment added no migration; its later direct-pair checkpoint used
ceiling 110. The current key-publication storage below raises the ceiling to 112.

[Participant evidence](measurements/collaboration-participants-2026-10-31.json)
records 181 API, 81 collaboration data, 14 collaboration domain, 22 identity data,
35 identity domain and 111 worker cases (one unchanged opt-in skip). Full relevant
selection, format and architecture checks passed in 5m16s. Corrected focused failures
retain the stable native error code and explicitly require positive native client
metadata. Existing approval/lifecycle selector completion checks also pass.

[Direct-conversation evidence](measurements/collaboration-direct-2026-10-31.json)
records 30 database, 14 domain, 89 collaboration data, 182 API and 111 worker cases
(one existing opt-in skip). Full selection, formatting and architecture pass in
6m57s; 23 Python recovery checks pass. Actual 109-to-110 upgrade and pg_dump/restore
preserve immutable identity, uniqueness and participant RLS. Corrected API test
uses the existing id/version response rather than inventing a replay flag.

[Room-adapter evidence](measurements/collaboration-room-adapter-2026-10-31.json)
records 17 domain, 96 data, 182 API and 111 worker cases (one existing skip); full
selection, formatting and architecture passed in 4m55s. Twelve Python ownership
checks, JS syntax and Vite build pass. No additional migration; ceiling stays 110.

Critical provider behavior: Synapse logout deletes the **entire device**, all its
bearers and E2EE keys even with an expired bearer. Never use it to clean up a late
or losing login; unpublished candidates expire privately within ten minutes.
Only irreversible HRIS device revocation permits logout. Synapse JWT expiry has
120 seconds of tolerance; HRIS must enforce its strict admission deadline itself.
Provider ports remain private; raw provider credentials never reach clients.

Next implementation:

1. Room adapter `13a8d4bf` passed every job/step in control [41203190380](https://github.com/fajarxfce/hris-platform/actions/runs/41203190380)
   and browser provider [41203190384](https://github.com/fajarxfce/hris-platform/actions/runs/41203190384);
   hosted browser case passed in 9.897s (30.626s owned run), cleanup true. Direct `031c3714` passed all jobs/steps in [41201832706](https://github.com/fajarxfce/hris-platform/actions/runs/41201832706);
   room proof `6139604c` likewise passed [41201965622](https://github.com/fajarxfce/hris-platform/actions/runs/41201965622).
   Participant `d50bb0ad` and identity `b0bf1326` hosted controls also passed.
2. Schema-111 storage `71ad7c81` passed all jobs/steps in [41205061024](https://github.com/fajarxfce/hris-platform/actions/runs/41205061024).
   Room setup/worker `f104f097` passed every job/step in `41206499276`. Own-device
   key gateway `05685bf2` passed every step in `41210683028`; schema 112 `966dee38` passed all hosted
   steps in `41208833880`. Scoped peer key query/claim and encrypted to-device are implemented above; continue
   classic sync and content. Offline recipient eligibility must not require a fresh short
   access token; retain current device/company/security revocation checks. Continue
   native/classic sync and content integration using the
   [typed adapter and invited-history policy](collaboration/matrix-room-adapter.md);
   Synapse createRoom/alias reservation is not atomic. Verify immutable creation
   marker, creator and complete encryption/membership state before publication
   (implemented for native direct rooms). Never-enrolled
   peers have no encryption key: retain pending outbox until an eligible device
   exists. Kotlin sender-to-offline-browser now passes; prove native recipient and Swift separately. Then group and
   resource-context membership control and bounded SDK key/sync/content operations
   with current per-conversation admission, cancellation and response fencing.
   Participant/DM metadata and WHOAMI/refresh do not authorize room contents.
3. Native/web SDK adapters, browser-parent admission, owned encrypted stores and
   durable chat UI/outbox/recipient reconciliation. Restore each SDK client only
   once; use standard refresh. Persist logical IDs before queueing, retain every
   provider event alias, handle conflicting reuse and redactions/recovery.
4. Remaining P0 gates and all P1–P8 groups: rich/context messaging, backup, native
   calls, web/mobile meetings, AI, recovery and measured capacity. Prove reusable
   Mobile foundations before porting them to Starter; no lab/backend port is due.

Live state stays at schema **103**, API/worker **2c2a59e3**, dashboard **1e0522c0**,
phone release **build 4**. No collaboration deployment or phone action occurred.
Dedicated emulator `hris-mobile-dev` / 5560 is stopped with data retained. Use
fictional owned provider fixtures and never real employee messages. Notify before
later phone installation; install only, preserve signer/data, versionCode >= 5.

## Completed feature audit — 30 October 2026

All 61 initial Platform requirements and the eleven detailed module reviews are
accepted, together with Mobile's fifteen-area review and the applicable Starter
foundation. Every confirmed gap/correction has implementation and matching behavior
evidence. [Final Platform CI 41094285531](https://github.com/fajarxfce/hris-platform/actions/runs/41094285531) passed all ten jobs at
`93438f99`, with 3,090 API cases, 104 active worker cases, 1,111 mocked
browser scenarios, 56 actual workflows and successful schema-103 recovery.
[The inventory](measurements/feature-audit-2026-10-30.json) and
[CI report](measurements/workforce-full-ci-2026-10-30.json) retain exact scope.

GitHub's step metadata lagged its successful terminal result. The final job log,
source-matching browser reports and uploaded artifact digest independently verify
all required steps, including cleanup. [The retained observation](measurements/workforce-ci-reporting-lag-2026-10-30.json)
explains the raw pending step fields; no cancellation, rerun or source change was
needed. The independent local 41-case diagnostic passed in 273.489s.

The authorized private API/worker update is complete using verified application
`2c2a59e3` packages after a fresh schema-103 recovery checkpoint. Both services
started and API health returned UP; dashboard HTTP 200 and its exact index bytes
are unchanged. Full migration history, restricted runtime roles, Google/person
bindings, active native session IDs, document revision integrity and record counts
are preserved. SMTP remains disabled. Old images and separate data/key checkpoints
are retained; [rollout evidence](measurements/final-development-rollout-2026-10-30.json)
records their locations and hashes. No production release was performed.

Mobile review `f4f101a1` retains full CI `d170810f`/`41077194465`: 1,994 desktop,
1,676 browser and 104 Apple cases plus complete Xcode/target builds. The normal
private APK with application `3c6db4eb` is already installed; this backend update
required no phone action. Starter review `7605916` retains accepted `77063862`/
`40916187442` and the separately proved distribution upload correction. All 657
identical shared files and 60 differences are reviewed; no generic port remains.
Physical iPhone testing is waived, as requested. Actual Android Google/Home/FCM
acceptance remains recorded; this rollout does not claim another provider test.

The [approved-overtime boundary](workforce-feature-review-2026-10-30.md#approved-overtime-boundary)
is explicit: the original PRD's immutable requested/actual/approved workflow,
attendance adjustments and financial Payroll amendments are implemented. A future
post-approval overtime-fact amendment has no defined product contract and is not
represented as implemented. Public domain/DNS/TLS, signed Apple/APNs deployment,
further tax/bank packs and production operation retain their documented separate
scope. No specified implementation, source CI or owned local test remains pending.

## Retained acceptance history

The following entries preserve earlier increments; the active queue above takes
precedence over their historical pending statements.

Metadata is fully accepted: [final CI and phone evidence](measurements/document-metadata-final-ci-2026-10-30.json)
records four passed Mobile jobs, 1,994 desktop/1,676 browser/104 Apple cases, full
Xcode build and the normal WireGuard APK installed in 5.777s without launching UI.
Installed hash `1a114fbc388c843b45749a8475296f506563493a38bccabd146323ceb87cf791`
is verified directly. Both `documents.02` and `people.03` are closed. Reporting's
seven export policy-completion reproductions now pass full local acceptance:
186 API, eight active worker, 52 domain and 61 repository cases plus quality and
packages (349.481s), then actual private CSV/browser recovery (45.417s with setup).
[Evidence](reporting-assurance-2026-10-30.md) and preserved JARs in
`.work/reporting-assurance-2026-10-30/packages` retain the exact candidate. Its two
leaf corrections are not deployed. Final source CI `41087903927` is now dispatched after
Administration CI `41081153648` passed all ten jobs. Those JARs
contain both corrections and cannot be deployed using Administration-only CI. Continue remaining PRD scope.

Administration correction `3c8fb15a` is committed/pushed and locally accepted.
[Full CI 41081153648](https://github.com/fajarxfce/hris-platform/actions/runs/41081153648)
passed all ten jobs for exact source `3c8fb15a6c2471eef226e5f5c20b0bd8b8ea450a`. Three
private readers and policy saving now check final enrollment/proof assurance;
eleven genuine reproductions pass with one prior ordinary control. Validation
passed 81 API, 104 active worker, eight Administration domain, fourteen data and
two Jobs domain cases, quality and both packages (242.548s). Three actual browser
workflows passed in 65.484s and one actual worker/browser workflow in 60.248s,
including setup. See [correction](administration-assurance-2026-10-30.md),
[five-row/six-section review](administration-feature-review-2026-10-30.md) and
[measurements](measurements/administration-assurance-2026-10-30.json). All five
Administration inventory rows are accepted; current counts are 41/20.
No Administration deployment occurred. Continue Reporting, Payroll and Workforce,
then full Mobile/Starter review while CI runs; do not close the full goal.


Complete every specified Platform/Mobile feature and port demonstrated generic
fixes to Starter. The [full audit](feature-audit-2026-10-30.md) remains active:
Approvals, Communications, Identity, Expenses, Leave, Documents, People and Administration now
have forty-one reviewed initial rows; twenty initial rows await completion/review,
followed by Mobile/Starter. Identity, Approvals, Communications, existing Document behavior, Expenses and Leave
have accepted full-CI evidence. The formerly missing Document category and separate
identity validity now pass complete native and replacement full-CI acceptance.
Do not mark the goal complete from these increments or earlier green CI.

Announcement email source `8014d733` passed all ten jobs in
[CI 41005223374](https://github.com/fajarxfce/hris-platform/actions/runs/41005223374):
four backend, four dashboard, dashboard integration and recovery. Its optional
selection, atomic queue, SMTP delivery, administrative observations and recovery
have [recorded acceptance](measurements/announcement-email-2026-10-30.json).

[Approval history](approval-history.md) is committed/pushed at `bbb99b48`, with
118 API/OpenAPI cases, 104 worker cases, 11 then-current domain cases, 4,012 dashboard
unit cases, 14 distinct Chromium scenarios and two real API/browser workflows.
One manual one-hour worker test remains covered by its earlier independent run.
The interrupted combined execution was replaced by successful separate API/worker
runs; [measurements](measurements/approval-history-2026-10-30.json) retain the details.

The approval correction completes assurance on seven older readers.
Ten actual acquisition-expiry reproductions failed at `bbb99b48` and then passed.
Final acceptance passed 60 approval API cases, 13 domain cases, architecture,
formatting, both packages and two actual API/browser workflows. Inbox completion
uses current-stage eligibility; retained request/history scope remains distinct.
See [correction](approval-read-assurance-2026-10-30.md) and
[module requirement review](approvals-feature-review-2026-10-30.md).
The correction is committed/pushed at `fcfdaf26`. [Full CI 41009137720](https://github.com/fajarxfce/hris-platform/actions/runs/41009137720)
passed all ten jobs for exact source `fcfdaf26a48718a80bc79468bdbdb3b4cbeae11c`.

[Communications read completion](communications-read-assurance-2026-10-30.md)
corrects thirteen reproduced MFA-expiry variants across ten older readers. All
106 API, 13 domain and three repository tests passed, along with quality/package
checks and actual API/worker/browser/SMTP acceptance (37.1s; 1.0m including setup).
The [Communications requirement review](communications-feature-review-2026-10-30.md)
locally accepts all five rows and nine detailed sections, including optional email.
The correction is committed/pushed at `737d39a7`. Final source `8e5dd3db` now passed all ten CI jobs, including the Document command correction.

[Identity](identity-feature-review-2026-10-30.md) accepts all five initial rows and
eight detailed sections against unchanged source trees and full CI `8014d733`:
190 API, 35 domain, 22 data and 17 worker cases, plus 95 Chromium scenarios
(including eight actual API/browser workflows). No new implementation was needed.

[Document read completion](document-read-assurance-2026-10-30.md) now passes all
20 reproductions, 223 API, 18 domain and nine data cases, quality/packages and four
actual browser workflows (42.4s). Seventeen readers retain final MFA checks,
including preparation and post-storage completion. No DI/schema change occurred.

[Document command completion](document-command-assurance-2026-10-30.md) corrects
eight commands. Nineteen actual baseline cases failed; inventory receipt replay
was the twentieth, already-correct control. All twenty focused cases and final
243 API, 18 domain, nine data, quality/packages and four actual browser workflows
passed (42.5s). The [Documents requirement review](documents-feature-review-2026-10-30.md)
locally accepts all five rows and ten original sections. Exact source `8e5dd3db49b2568917c44432574f58ce1eae6dae` passed all ten jobs in
[CI 41013958569](https://github.com/fajarxfce/hris-platform/actions/runs/41013958569).
This includes Communications and both Document increments; it does not include
the later Expense correction.

[Expenses](expenses-feature-review-2026-10-30.md) now locally accepts all six initial
rows and nine detailed sections. Its correction closes 29 failed cases: fifteen
payment-enrollment, three receipt-delegation, five mutable-input and six decision-
delegation cases. Three ordinary receipt-scope controls already passed; three added
delegator-account wait regressions now pass. Broad acceptance passed 329 API,
20 Expense domain and 115 Reporting cases, quality/packages (590s). Final decision
acceptance passed 62 API, 20 domain and quality/packages (145s), for 335 distinct
API cases across both selections. Four final real browser workflows passed in
1.2 minutes: claim authoring/review, payment preparation/release/reconciliation,
category policy and reporting. The [measurements](measurements/expense-assurance-2026-10-30.json)
retain both selections and package hashes. No runtime or phone update occurred.

Exact Expense source `5b60aba3fca4fe7ae388f57a08d6a9135b858739` completed
[CI 41018107365](https://github.com/fajarxfce/hris-platform/actions/runs/41018107365):
nine jobs passed; dashboard integration failed its five-minute worker-step budget
after all fourteen scenarios passed in 5.2 minutes. Later storage/export checks
were skipped. [Timeout evidence](measurements/ci-integration-timeout-2026-10-30.json)
records the outcome. The workflow now permits eight minutes for this step and
twenty for the job, retaining bounded test timeouts and zero retries. Replacement CI `41023645590` now passes all ten jobs, including unchanged Expenses.
No full-source acceptance is claimed from this failed run.

[Leave read completion](leave-read-assurance-2026-10-30.md) corrects eight readers.
Ten genuine reproduction failures now pass. Final acceptance passed 437 API/OpenAPI,
65 domain cases, quality/packages (822s), and six actual browser
workflows (51.5s including setup). No DI/schema/additional query change occurred.
Storage buffers are cleared and expired preparation starts no object read.

[Leave command/delegation completion](leave-command-assurance-2026-10-30.md) now
locally accepts all five rows and eleven original sections. Twenty-five actual
failed cases were corrected; five prior controls and four new account-wait
regressions remain distinct. Acceptance covers 485 distinct API/OpenAPI cases across a 471-case broad
run (857s) and 58-case final targeted run (115s),
plus 65 domain cases and quality/packages, six actual browser workflows (50.4s)
and three actual worker/browser workflows (53.4s), including setup. Nine leaf
use cases and one pure read policy changed; no schema/DI/transport change occurred.

Decision receipts select their original immutable pre-decision revision with a
one-item history page, preserving the correct approval through later cancellations.
They still require current independent assignment and exclude newly bound
beneficiaries. Read grants are reacquired after sorted actor/delegator account
locks. Request-management receipts retain current scope and final MFA. Do not
remove these bounded acquisitions as a query-count optimization.

Leave correction `70b92e59` is committed/pushed. Exact source `70b92e59e7b41bdafe9f9d1c4b9d5106412777f5`
passed all ten jobs in [CI 41023645590](https://github.com/fajarxfce/hris-platform/actions/runs/41023645590), including normal browser, worker,
private exports and recovery. [Evidence](measurements/leave-full-ci-2026-10-30.json)
retains exact outcomes and matching Expense tree. This CI excludes later People
work. Continue the full feature goal; accepted increments do not close it.

[Document category/identity metadata](document-identity-metadata-gap-2026-10-30.md)
is a newly confirmed implementation gap in `documents.02` and `people.03`. The
earlier Documents review omitted category; that row is reopened. Upload expiry
is not passport/permit validity, and Payroll tax residency is separate. The
current inventory is thirty reviewed and thirty-one unverified rows. People
command/Organization acceptance is complete. Backend/dashboard metadata is
locally validated; finish native projection/flows and recovery before accepting
the requirements.

[People read completion](people-read-assurance-2026-10-30.md) passes twenty-eight
actual reproductions across twenty-four readers, including both transfer-review
company scopes. Final acceptance passed 427 API/OpenAPI, 50 domain and fourteen
data cases, quality/packages (1124s), three actual browser workflows (36.5s)
and two actual worker/browser workflows (37.3s), with setup. No new query, DI
or schema change occurred. All five People initial rows remain unverified.

People reader correction `b0b7c21c` is committed/pushed. The later
[command/Organization correction](people-command-assurance-2026-10-30.md) now
passes fifty reproduced failures and four prior controls. All 54 focused API,
52 domain and fourteen data cases passed in 142s. The broad API selection passed
497 cases; its parallel worker run had two old enrollment-fixture failures.
After fixing only that fixture, all 104 ordinary worker cases passed with one
separately accepted manual test skipped; quality/packages passed in 226s.
Production/package hashes match the broad API run. Final actual browser workflows
passed in 36.1s (three normal) and 36.8s (two worker), including setup.
Both API and worker contexts are validated. Correction `158b81ac` is committed and
pushed. Exact source `158b81acce89f637dbf715bb573fffb7b99b92c9` passed all ten jobs in
[CI 41032184925](https://github.com/fajarxfce/hris-platform/actions/runs/41032184925),
including both People increments, normal/worker browser, private exports and recovery.
[Terminal evidence](measurements/people-full-ci-2026-10-30.json) retains every job and
step. It excludes the later V103 document metadata implementation.

Document business metadata backend and dashboard are now implemented and locally
validated; see the [contract and evidence](document-business-metadata.md). V103,
24 focused API cases and migration acceptance pass. Broad plus final corrected
suites cover 689 distinct API cases, 104 ordinary worker cases (one retained manual
skip), 27 database, 18 domain and nine data cases, with unchanged production/package
hashes. Dashboard passes 4,054 unit cases, 25 mocked and five distinct actual browser
workflows. Exact failed attempts and fixture-only corrections are retained in
[measurements](measurements/document-business-metadata-2026-10-30.json).

Metadata increment `27e7f77e` is committed/pushed. Exact source
`27e7f77e722da9e8ac2bde47836353ef92dcc3b3` completed
[CI 41038369597](https://github.com/fajarxfce/hris-platform/actions/runs/41038369597):
nine jobs passed; recovery failed because its reviewed schema ceiling remained 102.
The correction now seeds three metadata snapshots through the real API and verifies
all restored typed values, company RLS, history immutability and deferred head/history
consistency. Actual schema-103 recovery passes in 57.356s, with 104 objects and both
identity key generations; 42 Python tool tests pass. API/worker JAR hashes match the
accepted implementation. [Evidence](measurements/document-metadata-recovery-2026-10-30.json)
retains every original CI job and step. Correction `292a30f1` is pushed. [Replacement CI 41043798660](https://github.com/fajarxfce/hris-platform/actions/runs/41043798660)
passed all ten jobs for exact `292a30f14498b2eee97a9255f22fe645639658e5`, including
normal/worker browser integration, private exports and schema-103 recovery. Platform Git
retains every terminal job/step and the downloaded recovery summary: 104 objects,
2,098,795 bytes, restored metadata/history/RLS/constraints and completed cleanup.
The original run remains recorded as a failure; no CI run is pending for this source.

Mobile `7f352cfd` and `41fe79f9` are pushed: owned metadata format-2 refresh retains
old facts and admission gates; initial upload freezes category/identity/printed
validity in the encrypted intent. Old omitted payloads keep their original operation.
Root checks pass 1,878 desktop and 131 Document Chromium cases; the APK builds.
The new form also fixes validation feedback hidden above a long form. Mobile Git
retains each earlier fixture failure, the reproduction and source/APK measurements.

Mobile `e2690fd6` now recovers first definite upload rejections without reimporting
file bytes. Two genuine failures now pass; uncertain/unknown commands stay pinned.
Explicit reconfirmation uses an exact-file compare-and-set and new operation IDs.
All 1,888 desktop and 140 Document Chromium cases, root checks and the dev APK pass
(189.354s). Mobile Git retains cancellation/race/capability and actual encrypted
storage evidence. No phone or live API change occurred.

Mobile `3ce42fed` adds validated header/history/correction transport; `966e883c`
adds encrypted metadata/history and per-resource read admission. Both are pushed.
Current root checks, dev APK and Document browser suites pass in 184.621s: 1,912
desktop and 164 browser cases. Actual SQLite/IndexedDB tests retain 1,000 immutable
revisions and verify exact-page reads, restart, scope, lost keys, cancellation,
competing writes and pending/failed admission storage. These changes reuse existing
storage contracts and require no Starter implementation port.

Mobile `f282b027` adds admitted reads and the assembled detail/history route. Thirteen
domain and two UI cases cover ownership, finite paging, legacy support, cancellation,
offline history and denial recovery. Two reproduced publication failures now retain
an earlier denial when company policy expires during local storage. Root quality,
dev APK and Document browser suites pass in 197.437s: 1,927 desktop and 177 browser
cases, all without failures/errors/skips. Mobile Git retains exact cases, source/APK
hashes and earlier compilation/method-length corrections. This commit is pushed;
mutable working copies and the versioned correction editor remain in progress.

Mobile `b0f93d67` retains encrypted correction commands using the existing shared
CommandStore/key and exact record/revision checks. Three domain and six actual
SQLite/IndexedDB cases pass; full local checks covered 1,936 desktop and 186 browser
cases in 193.331s. `82519a85` adds explicit intake, admitted local observation, bounded
manual delivery and review. Fourteen new domain scenarios cover original retries,
current ownership/MFA, uncertain outcomes, later headers, acknowledgement and resource
subscriptions. Full root checks, dev APK and Document Chromium suites pass in 180.515s:
1,950 desktop and 200 browser cases, without failures/errors/skips. The first twelve
flow failures were a read-only fixture; it was corrected with a separate denied control.
Both commits are pushed. Mobile Git retains exact cases, sources and APK hashes;
no automatic delivery or current-header replacement is introduced.

Mobile `fbd6a717` and `36ddd5ed` complete encrypted mutable working copies and
admitted open/save/discard/observation, including matching-draft cleanup after
confirmed acknowledgement. Exact local acknowledgement recovery preserves concurrent
edits and the original reviewed header. Full checks passed 1,968 desktop/218 browser
cases and dev APK in 182.812s. `8ca77f19` then fixes a reproduced catalog exposure:
Back from denied details previously showed cached title/file. Per-resource gating
now redacts that record, preserves paging/other rows and permits neutral admitted
recovery. Full checks passed 1,973 desktop/221 browser cases and dev APK in 183.001s;
two screenshots were inspected. All commits are pushed with durable Mobile evidence.
These are HRIS business changes; no generic Starter implementation port is due.

Mobile `832bbc09` and `017aa506` now complete the locally validated metadata editor
and independent retained overview. The editor preserves incomplete input and the
reviewed header, coalesces local writes, freezes confirmed input and reuses original
operations after response loss. Acquired current facts remain separate from old
receipts. Two real command-admission failures and hidden stale-draft feedback were
corrected; its full checks passed 1,980 desktop/223 browser cases plus the dev APK.

The overview reopens local drafts/corrections after published binary rows disappear,
with bounded 20-copy pages, original cursors and neutral resource recovery. Opening
or reloading sends no HTTP or commands. Two reproduced presentation failures showed
that one failed reader could leave another stale private projection visible; both
readers must now be ready/admitted. Full root checks, dev APK and Document browsers
passed in 190.845s: 1,987 desktop/224 browser, zero failures/errors/skips. The actual
storage/controlled-HTTP UI cases pass offline draft recovery and original retry after
later administrative edits; three overview screenshots were inspected. Both commits
are pushed; Mobile Git retains exact cases, source/APK hashes and failed predecessors.
No runtime/phone/provider change or reusable Starter implementation port occurred.

Mobile `3c6db4eb` adds the bounded local metadata summary and direct overview link
in Account Pending actions. Full local checks passed 1,994 desktop/230 Document
browser cases and the dev APK (173.186s). Six domain regressions and the actual
Account workflow retain original command/draft deduplication, scope and disposal.

[Actual Android/API/storage acceptance](native-metadata-acceptance.md) now passes
for that application: two offline process restarts retain an incomplete draft and
then the read-only original uncertain command. Explicit retry confirms the same
operation/input after a later administrative correction. Done clears the matching
working copy and command; all seven pending groups empty. The independent observer
confirms three history revisions, the later version-4 header and unchanged binary
revision. Summary reloads stay 36→36 and 48→48; 78 native requests include exactly
two successful company writes. The fixture passed in 432.451s with complete owned
cleanup. Its earlier ten-minute backend lifetime failure is preserved separately.
See [sanitized measurements](measurements/native-metadata-2026-10-30.json).

Mobile full CI `41071828262` passed Windows/Web and iOS simulator compilation/tests,
but its Linux browser capacity case exceeded Mocha's default two seconds. The
1,000-revision assertions remain intact; a bounded twenty-second module test
configuration passes all 75 Document data browser cases locally in 21.607s.
The complete Apple app and replacement full CI still need terminal verification.

Next: finish Mobile full/Apple CI before accepting `documents.02` or `people.03`,
then the controlled schema-103 API/static-dashboard/Mobile rollout. Physical
iPhone testing is waived. Finish detailed People review, remaining Platform modules
and Mobile/Starter. Keep shared finance/leave evidence free of private identity
numbers. This business capability has no generic Starter port. The full goal remains active.

CompleteOffboarding and explicit onboarding/offboarding schedules already have
final checks; preserve their behavior. Client policy is admission-only: do not add
final availability rejection for already admitted work. Complete full People
requirements/evidence before accepting its rows; then continue the other modules
and Mobile/Starter.

Input copies are bounded before validation. Receipt reads retain scope/expiry and
ordered actor/delegator guards; one additional identity acquisition per short
receipt transaction is intentional. No DI/schema change or generic client port.

Downloaded
full-CI reports are supplementary local evidence in
`.work/feature-audit-2026-10-30/ci-8014d733`; parsed backend and browser indexes
are in `ci-suite-index.json` and `ci-browser-index.json` beside that directory.
The [Identity measurement](measurements/identity-feature-review-2026-10-30.json)
retains its selected evidence in Git. Gradle artifacts contain HTML, not XML;
Playwright HTML embeds the report ZIP with its verified source metadata.
Keep each requirement's matching evidence in the inventory. No reusable client
correction is due from these server/dashboard changes.

The **private development API still uses schema 101**, SMTP disabled. Preserve its
accepted Google binding and installed phone session. The dashboard service uses
`~/.config/systemd/user/hris-dashboard-dev.service.d/20-static-assets.conf`, pointing
Vite preview at `~/.local/share/hris-dashboard-dev/f91a9bca`. This baseline is compatible
with that API and independent of `apps/dashboard/dist`, which tests rewrite. Both
endpoints returned HTTP 200 and its index matched that snapshot. Future controlled
dev rollout must update both API and the explicit static asset path. No live mail,
phone interaction, production deployment or runtime rollout occurred in this audit.

## Current user direction — 29 October 2026

The user explicitly waived physical iPhone testing for this completion pass
("iphone gkpp gk ditest juga") and requested continued implementation through
completion, with each atomic commit pushed. Do not ask for an iPhone again or
keep that test as a completion blocker. Simulator/contract tests and Xcode builds
remain required; do not claim actual iOS sign-in or APNs delivery from them.
Configurable HTTPS admission/native associations, the public browser handoff and
the independently verified Starter port are complete.
The controlled domain remains a deployment setting, not a reason to leave the
configurable implementation unfinished. Preserve the accepted Google binding,
phone data and running private development stack.

## Native distribution audit — accepted

The audit fixes selected release API precedence over per-environment project
properties, optional app-link configuration through signing and packaging, and
31 missing SBOM POM verification entries. The original artifact hashes remain
unchanged. Android checks the normal test configuration before compiling the
selected release; Xcode forwards the selected backend/API explicitly. The 15
portable files retain Starter's application IDs, demo/remote choice and three-job
CI. HRIS's remote-only policy, build-version UI fixtures and explicit dev-network
XML attributes remain in Mobile.

Mobile source `3dc81d00` passed all 4 jobs in full
[CI 40909156570](https://github.com/fajarxfce/hris-mobile/actions/runs/40909156570). Downloaded Apple reports
confirm 104 tests in 28 suites, zero failures/errors/skips;
compilation/tests/Xcode took 791s/122s/1295s. The complete Xcode app, native/domain
artifacts and explicit selected distribution API/backend assertions passed.

Starter port `77063862` passed all 3 jobs in full
[CI 40916187442](https://github.com/fajarxfce/compose-fluent-starter/actions/runs/40916187442). Downloaded Apple reports
confirm 84 tests in 24 suites, zero failures/errors/skips;
compilation/tests/Xcode took 724s/81s/744s. The complete Xcode app, native/domain
artifacts and explicit selected distribution API/backend assertions passed.

Actual local signed Android APK/AAB/SBOM exercises passed in 215.572s on Mobile
and 136.647s on Starter. Both verify the selected release API and native manifest
in APK and AAB, actual signatures, retained domain files and checksums. Mobile
passed 1,860 desktop cases; Starter passed 326 desktop cases in 112 suites. Both
passed 17 distribution regressions. Normal dev builds and production SBOMs were
restored afterward (39s Mobile, 23s Starter), with fixture API/host values absent;
Starter's normal backend remains demo. Every disposable signing key was removed.

A workflow-only follow-up at Mobile `d7e445a8` and Starter `4c794e7` retains
`.well-known` files in the manual artifact upload. The pinned action's actual
`@actions/glob` 0.5.0 selection reproduced five files with both domain statements
missing on each signed fixture; the correction selects all seven and every
checksum matches. Both workflows include hidden files only in the packaged
artifact directory. This separate proof does not invoke upload; application/native
code and ordinary CI definitions remain at the full-CI sources above.

No CI, local check or reusable port remains pending for this audit. No distribution
workflow, upload or phone installation was performed; the installed Mobile APK
remains `72341ef6`. The private server and accepted Google binding are preserved.
Real signed Apple IPA, APNs, public domain/DNS/TLS activation, physical GPS and
production operation retain their separate target requirements. Physical iPhone testing was
explicitly waived; simulator and unsigned Xcode proof does not imply device delivery.

See [distribution evidence](measurements/distribution-2026-10-29.json).


[Detailed People review](people-feature-review-2026-10-30.md) now accepts four initial
rows against source-matched completed CI and all fourteen original sections. The
remaining `people.03` metadata condition awaits replacement Mobile CI `41077194465`
at exact `d170810f03852f46a948f9f4d5dea9ca4104bdbd`. Its predecessor `41071828262`
finished with Web/Windows/Apple success (104 Apple cases and complete Xcode app),
but Linux failed the browser's two-second capacity deadline. The corrected local
75-case browser run passed; do not relabel the original overall failure.

Administration review has started. Older private settings readers check MFA before
repository reads but not after; SaveCompanyClientPolicy similarly needs late
journal/receipt/enrollment review. These are source candidates only: reproduce
them before changing behavior. Public client-policy discovery has a distinct
authentication/recovery contract; do not add sensitive-reader MFA policy blindly.

The private pre-upgrade checkpoint `dev-before-metadata-20261030T150655` succeeded
with isolated verification (19.389s including restart), preserving schema 101 and
current Google/session state. API/dashboard are healthy again. New accepted
API/worker images and dashboard snapshot `~/.local/share/hris-dashboard-dev/1e0522c0`
are staged but not running. Older images have `before-metadata-20261030` tags.
Local `.work/metadata-rollout` retains private checkpoint/build/baseline details.
Continue controlled schema-103 rollout after Mobile CI; no phone installation yet.

## Previous implementation checkpoint — HTTPS links

The last implementation queue is closed: the [public HTTPS handoff](mobile-app-links.md)
passed 4002 dashboard unit cases and 14 related Chromium cases at `d21a1346`.
Native associations and bounded ASCII URL admission are implemented in Mobile and
ported to Starter after the recorded source proof. Actual Android scope/offline/
archive acceptance passed with 28 requests, no company writes and unchanged
read/ack state. [Measurements](measurements/app-links-2026-10-29.json) retain exact
sources, previous failures and all final verification.

Final Mobile source `72341ef6` passed all four jobs in full
[40895095518](https://github.com/fajarxfce/hris-mobile/actions/runs/40895095518).
Downloaded Apple reports confirm 104 cases in 28 suites, zero failures/errors/skips
and the complete Xcode app; compilation/tests/Xcode took 701s/77s/1040s.
Starter port `738db36` independently passed all three jobs in full
[40895798634](https://github.com/fajarxfce/compose-fluent-starter/actions/runs/40895798634),
with 84 Apple cases in 24 downloaded suites, no failures/errors/skips and Xcode;
compilation/tests/Xcode took 561s/84s/703s. Generated Android/Apple association
checks passed, and the Xcode/Apple artifact success markers were inspected.
No CI or owned local acceptance remains active from this increment.

The authorized phone has application `72341ef6`, installed with `--install-only`.
APK SHA-256 `5b3754d5097eb0a535d1a98cff5bc37e508ea12a008eec897895d9289797fe4d`,
32,872,857 bytes. Its registered certificate/private WireGuard API match, and
fixture values are absent from runtime code and the binary manifest. First-install
time was unchanged; no phone UI was driven. Actual Google/Home acceptance remains
attributed to the earlier `678783b3` user-operated APK. The private API/dashboard
still returned HTTP 200; preserve the Google binding, keys, volumes and running stack.

The implementation queue is complete for this development pass. Physical iPhone
testing was explicitly waived. Public domain/DNS/TLS association, actual APNs,
physical GPS and production deployment remain separate configuration/target
observations; no result for those is inferred from builds or fixtures. Default
native builds do not enable an unconfigured HTTPS host. Reopen implementation for
an observed failure or a documented requirement; do not recreate accepted modules
from old next-step paragraphs.

The user subsequently requested a usable server for the installed phone and
authorized continuing development startup. A new persistent private `hris-dev`
stack is running at schema 101; API health returned HTTP 200 from the VPS and
phone through WireGuard. Dashboard build/user service, private Garage/scanner,
separate database roles and development configuration review passed. The current
Android signing SHA-1 was registered in Firebase after exact-app verification.
Fictional company/employee provisioning is complete through public workflows:
operator MFA, captured worker invitation, employee role, independent person link,
schedule, demo Leave/Expense policies and four approval kinds. The same phone APK
passed Firebase email/password, company/Home and downloaded-document UI on the
owned emulator. The PDF passed real storage/scanner/worker publication and identical
download. A coordinated private recovery checkpoint passed schema/key/object checks;
host and phone health, browser login and document download passed after restart.
See [current runtime ownership, private paths and next actions](current-development-runtime.md)
and [sanitized evidence](measurements/development-runtime-2026-10-29.json).
The user tried Google and received the expected unlinked-account rejection.
Exact Firebase lookup then confirmed `google.com` and verified email. The old
fictional password binding was deactivated before adding the confirmed Google UID,
preserving V16's one-active-subject-per-issuer rule. The user retried and confirmed
Home; account-session metadata independently returned one new native session
created at `2026-10-29T10:24:50.263372Z`. Actual Android Google → Firebase → HRIS →
company/Home is accepted. Email and the binding receipt remain private. See
[Google evidence](measurements/native-google-2026-10-29.json). A newer private checkpoint
`dev-google-20261029T103103` now includes the accepted binding/session and passed
isolated restoration; API/dashboard and the same Google session passed after restart.
The initial Java-17 verifier failure and corrected JDK-21 run are retained in the
current-runtime record. Firebase Management
also supplied matching public iOS configs for all three HRIS flavors. Mobile's
iOS password/bundle loading and Google system-browser PKCE/code exchange passed
full CI `40858176601` at `43cd8ec6`: all four jobs, 97 native cases and Xcode.
Only then was the Google foundation ported to Starter `e0c6071`; its local checks
and all three jobs in full run `40864341588` passed. Downloaded Apple reports retain
77 native cases in 22 suites, no failures/errors/skips and the complete Xcode app.
The preceding password-only
Starter port `8a75de5` independently passed 62 native cases/Xcode in `40861505322`.
APNs/device acceptance remains. Leave
this persistent stack running; statements below about cleaned fixtures refer to
the earlier disposable acceptance runs, not this development environment.

The user supplied the Firebase service-account path. It is installed privately at
`/home/fajar/.config/hris/fcm-service-account.json` (directory 700, file 600), outside
Git. **The sender credential question is resolved; do not ask for it again.**
[Actual FCM acceptance](native-fcm-acceptance.md) now passed on Android 35 with
Mobile `678783b3` and Platform runtime `b7f42070` / schema 101: two real publications,
two observed generic OS notifications (foreground/background), correct notification
navigation, unchanged unread/unacknowledged version-zero Inbox rows and no native
company mutation across 55 requests. Disable/logout removed the server token/hash.
Fixture 6.2m/6.6m with setup; [measurements](measurements/native-fcm-2026-10-29.json)
retain the two preceding orchestration/observer timeouts and exact proof. Five new
configuration tests, 12 related fixture checks, seven proxy regressions, TypeScript,
Biome, build and architecture passed locally. All owned resources are cleaned.
This increment adds an opt-in fixture, not a deployment or application change;
there is no new generic Starter port. Actual Android Google exchange is accepted above; iOS/physical cases remain.

Corrected full Verify [40820912608](https://github.com/fajarxfce/hris-platform/actions/runs/40820912608)
passed all ten jobs at `9345c19bad4a219ecdb9ac247f944b2959c4642a`. All four backend and all
four dashboard shards passed. Downloaded reports confirm 2,731 disjoint API tests,
686 shared module tests and 94 worker passes; one opt-in elapsed-retention case
is skipped by ordinary worker runs and was separately accepted below. Dashboard
passed 3,997 unit tests in 352 files, seven native-proxy regressions and 1,098 distinct
Chromium cases. Repeated suites are counted once.

Schema-101 recovery and report upload also passed on this exact head. The downloaded
sanitized summary verifies 104 objects, 2,098,795 bytes, two encrypted records,
old/current keys, original-source preservation, typed audit/delivery projections
and cleanup. Actual integration passed 40 API/browser cases (4.9m), 14 worker cases
(4.5m), one storage export case (47.8s), two mail and nine storage fixture checks.
Those Platform runs and the newer Starter run are complete. Logs and downloaded summaries
were inspected; [tracked measurements](measurements/ci-2026-10-29.json) retain results.

The two preceding failed runs remain in [measurements](measurements/ci-2026-10-29.json).
Run `40816036721` passed tests/restore but excluded the hidden `.work` report during
upload; `70e5098d` enables only the exact sanitized summary glob. Run `40820005213`
passed backend/restore/upload but one browser locator matched both page and section
headings. `9345c19b` waits for the returned table and selects the level-one heading;
its four local Chromium cases and the corrected full dashboard matrix passed.

The final run used owned temporary ref `ci/closing-report-40820005213` so the
previous recovery run could finish without cancellation. That ref was removed
after verification; the tested commit remains on `feat/firebase-auth`. The newer
FCM acceptance fixture has separate local proof above. No PR, release or live
deployment was created.

Mobile's original full CI passed Linux/Android, Web and Windows but Apple cold
compilation exceeded its combined deadline. Workflow correction `451a2c4d` passed
[40817592306](https://github.com/fajarxfce/hris-mobile/actions/runs/40817592306):
27 simulator tests, complete unsigned Xcode build and retained reports. Only then
was it ported to Starter `1817e45`, whose independent
[40820482699](https://github.com/fajarxfce/compose-fluent-starter/actions/runs/40820482699)
also passed 27 tests, Xcode and reports. No generic port remains from this increment.
Mobile copy-only `678783b3` corrects English Expense item-count wording; local root
checks and Android assembly passed with 1,840 desktop cases in the report set.
Its hosted CI and previous native recovery used earlier application `02743a9f`.

## Workspace and delivery policy

| Repository | Working branch | Accepted source boundary |
| --- | --- | --- |
| `hris-platform` | `feat/firebase-auth` | Runtime integration `b7f42070` / schema 101; full CI `9345c19b`; later real-FCM fixture accepted locally |
| `hris-mobile` | `feat/native-push` | Final application `72341ef6`: all four jobs in `40895095518`, 104 Apple cases/Xcode; installed on phone with data preserved |
| `compose-fluent-starter` | `fix/push-token-lifecycle` | HTTPS port `738db36`: all three jobs in `40895798634`, 84 Apple cases/Xcode; local 326 desktop/11 browser and configured artifact checks |

The repositories are siblings under `/home/fajar/hris`. Inspect Git status, history
and upstream before editing; preserve unrelated work. Mobile was generated from
Starter and is the proving ground. Prove reusable changes there before porting to
Starter; HRIS policy, APIs, routes and configuration remain in the consuming repos.
Read the [Mobile handoff](https://github.com/fajarxfce/hris-mobile/blob/feat/native-push/docs/agent-handoff.md)
and [Starter handoff](https://github.com/fajarxfce/compose-fluent-starter/blob/fix/push-token-lifecycle/docs/agent-handoff.md).

Routine implementation, validation, atomic commits and pushes are already authorized.
Do not ask again or add co-author trailers. A Git push does not authorize releases
or a live cutover. Tracked contracts/measurements are the durable handoff; local
`.work`, `PRD-PROGRESS.md`, `REPO-TRACE.md` and chat are supplementary.

## Accepted product evidence

Use the [eleven-module map](prd-status.md#implementation-and-acceptance-map) and
[delivery ledger](completion.md#detailed-delivery-evidence-by-area) for the exact
implemented scope. Do not recreate completed features from an old future-work paragraph.

- All nine Mobile company data-sync registrations and six pending-action observers
  are implemented. [Native recovery examples](prd-status.md#native-pending-action-coverage)
  cover Inbox, Attendance, Overtime, submitted Leave, saved Expenses and document
  transfers/inspection review, with exact replay and server invariants. They do not
  prove every state combination or physical target.
- [Push delivery observations](announcement-delivery.md), `409abad4`, and
  [original payroll comparison](payroll-period-comparison.md), `f757bffd`, are
  integrated at `b7f42070`, with actual API/worker/browser and schema-101 recovery
  evidence. Neither administrative feature needs a Mobile/Starter business port.
- [Elapsed export retention](export-retention-acceptance.md), `1abdbce9`, passed
  its unchanged physical one-hour grace, lost delete receipt retry and one audited
  retirement. JUnit: one pass, 3,860.892s; Gradle 1h4m32s, compiled `ecb17627` / schema
  100. It completed at 03:17:16 UTC; no running test/classpath must be preserved.
- [Workload evidence](workload.md) covers 20,000 attendance requests, a constrained
  5,000-employee payroll calculation and full-signature document scanning with two
  100-MiB PDFs, checked downloads and EICAR rejection. These are distinct measured
  fixtures, not a claim about an untested production environment or mixed workload.

All native-owned fixture services, containers, control files and forwarding are
cleaned. The dedicated emulator is stopped with app data retained and location
restored. Do not rerun accepted expensive workloads without a new failure or
requirement. New behavior gaps still require implementation and relevant acceptance.

## Remaining delivery areas

The preceding Apple provider ports are accepted. Configurable HTTPS admission and
native associations are implemented in Mobile `d36a1bc9`; full CI `40887772486`
passed all four jobs and 103 native cases/Xcode. Its Starter port and final parser correction are accepted by the complete CI
runs above; no implementation or test remains active from this increment. The public dashboard handoff
and actual Android scoped/offline/archive case passed. The user waived physical
iPhone testing for this completion pass. Deployment-specific evidence stays
separate from these implementation results.

| Deployment observation | Configuration/execution still needed |
| --- | --- |
| Public HTTPS association | A controlled domain, its DNS/TLS and generated public association files served without redirects. Configurable native implementation already exists; do not implement it again. |
| Apple physical provider/push | Optional later device signing and APNs setup; actual device testing is waived for this completion pass. Simulator/Xcode proof is recorded independently. |
| Physical location and production | Authorized device/target environment and target-specific execution. Emulator/no-location review and isolated recovery retain their measured boundaries. |

Only the domain question remains pending; do not repeat it while waiting, and do
not request an iPhone again. Mobile's [optional iOS procedure](https://github.com/fajarxfce/hris-mobile/blob/feat/native-push/docs/ios-acceptance.md)
is a future procedure, not an executed test. Preserve the accepted Android Google
binding/session and the running private development stack.

Do not invent provider credentials, bank formats, tax rules or new scope to close
these rows. The initial expense bank submission is manual. New workload corpora or
rule packs need an explicit requirement, not inference from historical prose.

## Firebase state and external inputs

The user selected both FCM and Firebase Authentication in project `cmp-fajar` and
enabled Email/password and Google. Ignored local Android config exists at
`apps/android/google-services.json` in Mobile and Starter; do not print/commit it.
The public Google web client ID is
`415338369394-nop83je85ci1btt097rpl21r6sm96ldv.apps.googleusercontent.com`.

Actual Firebase email/password → HRIS session/MFA/company/logout passed; the temporary
provider user was removed. Actual SDK FCM token issuance and HRIS device registration
also passed. The supplied server credential now passed real foreground/background
FCM delivery and notification navigation on the owned emulator. Public Android
configuration remains separate from the sender credential; no private key belongs
in chat or Git. Scope replacement/duplicate/revoked hints retain their separate
automated coverage; this actual provider case used the correct selected account/company.

## Commands and environment

Use JDK 21 at `/usr/lib/jvm/java-21-openjdk`. Docker group membership on this VPS
requires the existing wrapper for new owned fixtures and backend tests, for example:

```sh
sudo -n runuser -u fajar -g docker -- env \
  JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  PATH=/usr/lib/jvm/java-21-openjdk/bin:/usr/local/bin:/usr/bin:/bin \
  ./gradlew --no-daemon check :apps:server:bootJar :apps:worker:bootJar
```

Use relevant focused targets for a scoped change; root tasks are `architectureCheck`
and `formatCheck`. The project is `:apps:server`, not `:apps:api`. JAR paths are
`apps/server/build/libs/apps-server.jar` and `apps/worker/build/libs/apps-worker.jar`.
Use `--no-daemon` when Docker group ownership matters. Avoid concurrent heavy local
builds/workloads on measurement CPUs. Follow the particular fixture's owned cleanup;
never use global Docker prune or remove retained data belonging to a live fixture.
Successful recovery summaries are emitted only after their owned cleanup passes.

Dashboard commands run in `apps/dashboard` with Node 24. Verify a development branch
with `gh workflow run ci.yml --ref feat/firebase-auth`; inspect exact commit, all
jobs and actual artifacts. Branch concurrency cancels older runs on that same ref.
Mobile/Starter handoffs have copyable JDK/SDK/Chromium environment commands.

Use only AVD `hris-mobile-dev`, serial `emulator-5560`, for routine native work;
never wipe its data. The acceptance APK uses a local fixture API override, so do
not distribute it as a normal configured build. Phone delivery follows Mobile's
`AGENTS.md`: notify before installation, use install-only and do not drive phone UI.

Keep current status, exact proof, limitations and next work in each accepted commit,
then push and verify upstream. Preserve historical failures in their measurement
record without leaving obsolete instructions in this active queue.
