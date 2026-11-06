# Android SDK through the HRIS gateway

[Rejected SDK authentication and original-gateway recovery](native-sdk-auth-recovery.md) now has an actual Android/API/PG/Synapse proof alongside normal access renewal.

[Original SDK access renewal](native-sdk-access-renewal.md) now exercises expiry
after store restoration while retaining encrypted history and queued transactions.

## Android incoming gaps beyond the SDK window validated — 2 November 2026

[Evidence](../measurements/chat-incoming-history-waves-2026-11-02.json) verifies three
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

The pinned Android Matrix SDK now exchanges encrypted DIRECT messages in both
directions through the actual HRIS HTTP gateway and an isolated Synapse. Two
fictional employees use separate encrypted SDK stores. Their public device keys,
recipient key query/claim, encrypted to-device envelopes, room sends and classic
sync all pass through current native HRIS admission. The server reads both
resulting events through the same gateway and verifies ciphertext, sender and
absence of a plaintext body. The client verifies the expected body and encrypted
`org.hris.message_id` after decryption. See [evidence](../measurements/collaboration-native-hris-sdk-2026-10-31.json).

The HTTP fixture uses real registration and bootstrap. Device activation and
room-binding publication are explicit SQL fixtures; provider room creation is
real. This test does not replace the separately tested provisioning worker, and
does not accept product outbox persistence, multi-device verification, notifications,
Apple interoperability, calls or meetings. No production service or user phone
is involved. No room/device cleanup calls Synapse logout.

## Protected native store integration

The proof now uses Mobile's actual `AndroidCredentialStore`, Keystore,
backup-excluded directory and exclusive runtime lease. Each fictional owner checks
competing opens, missing credential records and an incorrect store passphrase,
then restores the original record and verifies unchanged Ed25519/Curve25519
identity before exchanging messages. The separate process-death scenario below
also verifies the pending SDK send queue.

The first full exchange exposed an incorrect gateway constraint: after a failed
initial upload, the pinned SDK sent 50 one-time keys plus one fallback key whose
ID overlapped a one-time key ID. These are separate upload maps. A bounded test
trace confirmed the two counts, one overlap and valid fallback markers without
retaining public-key bytes, signatures, identities or bodies. The earlier rule
rejected that shape before signature verification, so a peer never became ready.

The corrected snapshot preserves both maps independently. Bounds, device identity,
each Ed25519 signature and the signed fallback marker remain mandatory. Independent
signature vectors include same-ID success and tampering failures; the real Synapse
adapter test uploads and replays overlapping IDs without replacing either map.
The original 74s timeout and 46s diagnostic failure remain in the
[protected store measurement](../measurements/collaboration-protected-native-store-2026-10-31.json).
The corrected Android exchange passes in 22.732s with cleanup complete. Full 454s
regression passes 37 domain, 201 data and 39 API cases plus quality; the driver has
27 passing cases. Mobile root/debug/lint/browser checks pass in 78s with 2,063
desktop and 24 data browser cases. Fifteen Platform and seventy Mobile source
hashes bind the APK and behavior to the checked code. No product group is accepted.

## Pending queue across process death

The stage process pauses queues before restore/subscription, enqueues exactly one
message and waits for the SDK's durable local transaction receipt. The driver then
force-stops only the proof package, verifies that PID is gone and starts a second
instrumentation process without reinstalling or clearing data. That process restores
the existing protected records, compares both device-key fingerprints and observes
the same replayed transaction. It does not bootstrap or enqueue a replacement.

Before releasing the retained queue, the fixture confirms no encrypted room-send
request reached the gateway. The observation happens at filter entry and cannot
hide an early request that finishes later. After release, both clients decrypt the
expected bodies/logical IDs; the server verifies encrypted events. Driver checks
reject unchanged PIDs, changed transactions and completion before restoration.

[Process-death evidence](../measurements/collaboration-sdk-process-death-2026-10-31.json)
retains APK `a14d6bf1`, 15 Platform/71 Mobile source hashes, actual SDK 33.512s and
successful cleanup. The complete fixture/quality run passes in 62s: four API cases;
32 driver cases also pass. This establishes SDK persistence for the original pending
transaction. App outbox reconciliation after provider transaction expiry, push,
OS wakeup and Apple runtime remain separate acceptance work.

## Interrupted synchronization after restart

The fixture now arms one standard Matrix `M_LIMIT_EXCEEDED`/HTTP 429 response only
once the second process has restored its store and pending queue. It waits for a
sync containing `since` before releasing the retained send. The trace hashes that
cursor in memory and requires a later successful sync with the same digest. No
cursor, digest, bearer token or message body enters the diagnostic report. Both
encrypted directions, original transaction/device identity and complete cleanup
remain required. The injected fault exists only in this test filter.

This reproduces Mobile hosted run `41250076331`: provider-capacity 429 responses
ended the pinned FFI classic sync loop, whose listener reports no error. Mobile
now owns a bounded `syncOnceV2` transport pump using the SDK's persisted cursor.
Success after a fresh process or a reset cursor is insufficient for this scenario.
The server's production capacity limits and admission behavior are unchanged.

## Sync compatibility

SDK 26.10.23 pins [Rust SDK 8de215a0](https://github.com/matrix-org/matrix-rust-sdk/blob/8de215a0aa0026afa5754ae5f05a6d168d1a1d60/crates/matrix-sdk/src/client/mod.rs),
which always sets `use_state_after=true`. Its pinned
[Ruma caf1ac05 serializer](https://github.com/ruma/ruma/blob/caf1ac05a9fd347158b60505ef8546a2ff18ae7c/crates/ruma-client-api/src/sync/sync_events/v3.rs)
sends that **unprefixed** query key. The gateway accepts exactly one boolean
preference while retaining its advertised Matrix v1.12 `state` response. It does
not advertise state-after support or fabricate `state_after` from an earlier
state base. The actual SDK correctly consumes the standard response. Invalid or
duplicate preferences, arbitrary filters and other unknown queries remain closed.

Earlier attempts timed out after successful key/message sends because the sync
loop received 400. Initial code-only tracing identified an unknown query; the
first attempted MSC-prefixed spelling also failed. The exact serializer resolved
the mismatch. The measurement retains these failures and successful correction.
One concurrent key upload may receive the existing `collaboration_provider_busy`;
the actual SDK handles the transient outcome and completes the exchange. This is
not a claim that every concurrent request succeeds or that the app owns recovery
from a terminated sync loop yet.

## Reproduction and resource ownership

Build `hris-mobile`'s separate `:apps:collaboration-proof:assembleDebug` artifact.
Boot only the existing `hris-mobile-dev` AVD at an unused allowed serial. On the
shared development host use `emulator-5562`; `5560` currently belongs to another
project. Never replace that AVD. With JDK 21,
Docker access and `ANDROID_HOME`, run from Platform:

```sh
HRIS_COLLAB_NATIVE_SERIAL=emulator-5562 \
HRIS_COLLAB_NATIVE_APK=/absolute/path/collaboration-proof-debug.apk \
  ./gradlew --no-parallel --max-workers=1 :apps:server:cleanTest \
  :apps:server:test --tests '*NativeCollaborationSdkInteropTest'
```

`HRIS_COLLAB_NATIVE_SERIAL` applies to all three native driver families and accepts
only5560/5562. Legacy default5560 is retained; always set the actual owned serial on
a shared host. The selected serial must still identify the `hris-mobile-dev` AVD.

Without the opt-in APK variable the class is skipped. Do not run heavy Mobile
builds alongside this provider/emulator test on the shared VPS. Source changes
to the Python driver require `cleanTest`, because they are not Gradle test inputs.

The driver verifies the exact dedicated AVD and debug package, refuses existing
unowned proof data/mappings, and locks emulator ownership. It passes fictional
credentials through app-private stdin, not shell arguments. IPC files are private,
atomic and never overwrite old evidence. The Java fixture always removes its
credential file; the driver uninstalls only its proof package and removes only its
reverse mapping, including cancellation and failed startup. If a child does not
terminate after bounded terminate/kill waits, cleanup still attempts the package
and mapping and reports failure. Normal HRIS app data is retained.

Only bounded code categories, static endpoint shapes, status, numeric timeout,
safe error identifiers and validated fictional receipts enter retained evidence.
The test-only trace caps observations at 512; bodies, credentials, cursor/filter
values and raw resource identifiers are excluded. Trace counters are diagnostic,
not a gateway allowlist. Tests exercise invalid endpoints/evidence, private IPC,
partial results, cancellation, failed installation/mapping and cleanup failures.

Continue admitted bootstrap installation/recovery, application session ownership
and encrypted logical-ID reconciliation, then UI and all remaining P0–P8 work.

The current [entry/recovery evidence](../measurements/collaboration-sync-recovery-2026-11-01.json)
retains the obsolete-owner fixture correction and explicit bounded retries of a
provider-busy join. Fixture retries are distinct from automatic sync transport
recovery; each product entry call still executes once under its current owner.

## Common sender and receipt projection

The [sender integration](../measurements/collaboration-sender-integration-2026-11-01.json)
uses product HRIS setup/conversation API adapters and the common sender against
the same actual SDK runtime after staging process death. A sender-only disposable
HRIS credential remains in private fixture input; artifacts never include it.
The fixture seeds local login/workspace state and records actual server admission
counts: device/conversation/room each succeed three times, with two rounds before
the first encrypted send and the third before command projection.

After the real ACK is journaled, a test repository deliberately refuses `Sent`.
The fixture reopens the real app database and verifies both retained receipt and
original staged intent, then uses product local reconciliation once and checks
idempotent replay. Independent SDK receipt observation, both encrypted directions,
native keys/cursors and the paused sibling room still pass. Strict client proof
flags and separate server counters make these checks mandatory. This is database
reopening after the injected fault; actual process death after ACK remains a
separate history-reconciliation case.

## Original history, provider references and offline resolution

The Android fixture discovers the latest encrypted reply, acquires original text
through the common admitted history use case, and stages bounded provider metadata
with its durable continuation. It reopens the encrypted database, checks all
references, replays the initial position and continues from restored progress.
Device/conversation/room admission must increase between each provider request.

Offline reference reconciliation then matches the two retained original events,
removes only their pending indexes and preserves every unresolved metadata entry.
The fixture checks unchanged provider position/total references, one revision
increment, persistence across another database reopen and a second reconciliation
that makes no write. The strict `providerHistoryReferencesReconciled` flag is
required by Android evidence extraction, the Python driver and server verification;
numeric truthy substitutes are rejected. Original text bodies are verified afterward.

The proof then explicitly requests the latest page using the common acquisition
operation. Its new position commits without deleting unseen references or reviving
resolved ones; another database reopen and reconciliation must leave them intact.
`providerHistoryRestartPreserved` is a mandatory Boolean across client, driver and
server evidence. The independent gateway trace requires 3–4 metadata requests:
unpositioned first/last, positioned requests between them, and increasing admitted
device/conversation/room reads around every request. The final count is at least ten
for each resource. This exercises explicit restart, not retirement of a real server
cursor-encryption key or automatic origin switching after a provider rejection.

See the [Mobile history contract](https://github.com/fajarxfce/hris-mobile/blob/feat/communications/docs/collaboration-history.md#offline-reference-reconciliation).
This is a bounded two-original fixture, not complete decryption, expired-cursor
recovery, an ACK-before-journal process-death proof or product/capacity acceptance.

## Explicit history-runtime reopening

The dedicated fictional Android fixture now fills the product client's 16 retained
focus slots, including failed context requests, and requires the next distinct
anchor to return the capacity error. It holds a real sibling-room queue writer
while invoking the common `ReopenCollaborationHistory` use case, requiring a busy
result with the original transaction and runtime still owned. After releasing that
writer, it requests reopening through the same protected repository and live HRIS
admission flow. Complete SDK draining can outlast a single five-second caller
wait. The fixture makes at most six explicit common-use-case requests, continuing
only for `client.collaboration_close_pending`, as with the earlier protected-store
proof. Other failures stop immediately; the product use case never retries itself.

The fixture requires the restored client to keep the same key fingerprint and
bootstrap operation, to decrypt the original sent event, and to retain the sibling
room's original transaction while its send queue stays paused. A repeated readiness
request must return `Available` without another restore. The encrypted provider
reference checkpoint is compared before and after another application-database
reopen. Every raw observer is disposed before client shutdown; replacement cleanup
runs even if an assertion fails. `gateway-result` is published only after this
scenario completes; `historyRuntimeReopened` is a required Boolean across Android,
Python and the server verifier. This does not assert a newly synchronized SDK cursor,
large-room capacity, or product UI acceptance.

## ACK before journal across a second process death

The fixture now appends a separate message after the existing staging, encryption,
receipt-projection, provider-traversal and SDK-reopening scenarios. It stages the
actual plain-text payload through the common use case, retaining protected sender
and full original-content hash before enqueue. A fixture receipt adapter suspends
only after the native ACK has arrived and the absent journal row is verified. The
checkpoint contains PID, transaction and interruption flags, never the ACK event
ID or body. The driver force-stops that exact process, confirms its exit and starts
a third process with the same protected store. A restore barrier verifies its new
PID and retained transaction before recovery can proceed.

Latest admitted original history supplies the receipt evidence. Common local
recovery appends it once, leaves the original command Staged, and the separate
journal reconciliation projects Sent after reopening the database. Empty native
queue state is checked but never used as acknowledgement evidence. The original
intent, transaction and full-content identity survive. Literal Markdown/HTML,
emoji and newline characters exercise the explicit plain-text payload identity.

[ACK recovery evidence](../measurements/collaboration-original-receipts-2026-11-01.json)
records run `39775a6b851040e19d1f4c97059025c8`, **41.453s case / 62.142s suite /
91s Gradle**, cleanup true, 22/22/22 device/conversation/room admissions and zero
failures. The gateway independently verifies all three ciphertext events and
exactly three successful encrypted sends; the held room never sends. Both latest
history reads require distinct live admission boundaries. Mandatory evidence adds
`ackBeforeJournalRecovered`, driver-owned `processRestartedAfterAck`, and
`encryptedSendCount: 3`. All 40 Python driver cases pass, including mismatched PIDs,
transactions, ACK/journal flags and terminal evidence. This extends Android fixture
evidence; it does not accept Chat UI, native background delivery or other platforms.

## Interrupted completion retirement

The [retirement proof](../measurements/collaboration-retirement-2026-11-01.json)
extends the actual exchange to four distinct processes. After repairing the ACK
journal, common retirement archives both sent commands and stops before exact journal
cleanup. The driver validates the third PID and original transaction, force-stops it,
and admits a fourth process only after the old process is gone. The final process
starts neither sync nor delivery. It validates both completion proofs, runs common
reconciliation/recovery, removes the two receipts, reopens SQLite and checks that
matching insertion remains completed while changed-intent insertion is rejected.

Both `completionCleanupRecovered` and driver-owned `processRestartedAfterCompletion`
are mandatory. Archive/receipt checkpoint booleans are strict; checkpoints carry no
message body or event ID. Final proof must belong to the fourth PID and retained
transaction. The server still verifies exactly three encrypted sends, no held-room
send, original identity and complete cleanup. This accepts the interruption scenario,
not Chat UI, push, backup, calls, meetings or product capacity.


## Frozen draft recovery across Android process death

The native application-worker fixture now runs two independent cases: ordinary QUEUED
intent and FROZEN_DRAFT before command admission. The latter requires zero pending commands
and the same stored draft revision, submission ID and body hash both before and after actual
process death. The real app worker admits that original, sends one encrypted message,
archives its original receipt and confirms draft clearing. Both cases then acquire the
existing 32 encrypted peer seeds and exercise the foreground history route.

[Evidence](../measurements/chat-frozen-native-2026-11-01.json) binds 44 Mobile/four Platform
inputs and proof APK `1a664545`. The combined 227s run passes four cases in three suites:
SDK, WorkManager, ordinary queue and frozen draft. The two workers each finish in one pass,
with four history actions, 43 references, ten state-only references and 30/28/30 application
admissions with zero failures. Initial scheduled-validator and provider-setup failures are
retained. PREPARED remains a valid scheduler handshake; each worker case owns its provider
and Spring trace state. The original provider failure code was not captured, so its exact
cause is not asserted; future setup errors retain safe kind/code diagnostics.

Local execution is separate from hosted CI. Pin the committed Platform fixture in Mobile
and audit a matching hosted run. This does not accept physical iPhone/web behavior, capacity,
all chat features or the complete communications PRD. The installed release8 predates these
fixture-only changes; no additional phone installation follows from them.


## Incoming recovery without an outbox or workspace selection

The [incoming provider proof](../measurements/chat-incoming-provider-2026-11-02.json)
closes the receiver SDK before 32 encrypted peer seeds, then opens a background-only
application container. Its workspace repository rejects selected-workspace access.
Fresh real native-device directory/admission and summaries drive the actual incoming
worker with an empty outbox. Each of three draft/queue variants completes in one pass,
persists 12 originals (including the previous outgoing original), and verifies exact
retention after reopening storage. All borrowed runtime resources close before the
normal foreground history probe resumes and checks the full fixture history.

The driver validates six bounded boolean assertions, pass/original counts and fresh
HTTP acquisition counts. A separately retained incoming checkpoint preserves evidence
when a later foreground assertion fails; neither bodies nor opaque IDs are copied.
SDK and scheduled offline/process-death regressions remain in the same five-case run.
This phase keeps the Android process and peer SDK alive and directly invokes recovery;
it does not prove incoming FCM, network-offline/Doze wake, all-company scheduling or
complete long-offline catchup. Login/provider bindings remain explicit fixtures.


## Provider bounds for independent incoming waves

[Boundary evidence](../measurements/chat-incoming-history-boundary-2026-11-02.json)
validates the existing encrypted Matrix `from`/`to` gateway repository against isolated
Synapse. After25 events,47 more arrive; the next wave reads7 per page up to the saved
previous start. Five further sends occur after its first page. Recreating the adapter
between pages preserves the exact stop bound: the partial wave yields only its47
original events, the following wave yields all5 later events, and another wave is
empty. The original full-history/context/direction assertions remain in the same case.

These are provider metadata with synthetic encrypted payloads, not SDK decryption or
Mobile worker acceptance. Both positions stay scoped to company/account/device/server/
room. Mobile still needs to persist and carry both boundaries through its incoming
traversal. This avoids inferring scan completion from timestamps or incidental event
overlap. Invalid/retired cursor recovery and visibility changes remain separate checks.
