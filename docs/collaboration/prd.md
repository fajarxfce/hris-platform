# HRIS collaboration

Accepted 31 October 2026. This is new work after the original HRIS audit, not
evidence that chat or calling already exists. The user authorized implementation
of the entire plan, atomic commits with pushes, and Git handoffs. Mobile is the
proving ground; reusable, verified foundations subsequently return to Starter.
See [delivery](delivery.json), [architecture](architecture.md), and
[threat model](threat-model.md). All phases below belong to the completion target.

## Accepted decisions

- Initially 5,000 employees, with company-scoped employee conversations.
- Android, iOS and web support chat, voice/video calls and meetings.
- Self-host Matrix Synapse, LiveKit, recording, speech recognition and AI.
- One large meeting at a time: 1,000 attendee connections, at most 500
  interactive and 500 viewers. Companion devices consume connection capacity;
  service participants need separately reserved capacity in load tests.
- Invited external guests require host admission and receive meeting-only access.
- Chat and private calls are E2EE. Meetings have an ordinary transport-encrypted
  mode and an E2EE private mode, immutable for the life of the occurrence.
- Ordinary meetings support visible, authorized recording/transcription/AI.
  Private meetings prohibit server recording, transcription and AI. Their chat,
  polls, questions and whiteboard payloads/snapshots remain encrypted.
- Reimbursement supports both DMs with a resource card and claim-specific rooms.
  A replacement approver sees messages from joining; an authorized participant
  can select history to hand over through E2EE. No automatic history disclosure.
- AI includes captions, transcripts, Indonesian/English translated captions,
  summaries, decisions, action items, catch-up and transcript-grounded questions.
- Google/Outlook calendar integration and Google Drive/iCloud encrypted backup.
- PSTN, dedicated room hardware, and translated speech audio are outside this
  accepted package. Caption translation is Indonesian/English.

## Feature inventory

Each ID is an acceptance group; every behavior in its row must be accounted for.
An isolated provider experiment or a domain unit test does not accept a whole
group. Delivery evidence distinguishes source tests, actual services, devices,
external providers, rollout and capacity.

### Chat

| ID | Required behavior |
| --- | --- |
| CHAT-01 | Company-scoped employee DM from directory, profile, search and work context; active HRIS identity and admission. |
| CHAT-02 | Groups: name, avatar, description, members, admins, invitations, leave, invitation policy and membership history. |
| CHAT-03 | Multiline text, basic formatting, emoji, mentions, links, quoted replies and reactions; safe data rendering. |
| CHAT-04 | Owned offline history, drafts, durable message/attachment queue, cancel pending, bounded retry and recovery after process/device restart. |
| CHAT-05 | Pending, sending, server-accepted, device-delivered, read and failed; delivery acknowledgement follows recipient durable storage. |
| CHAT-06 | Edit with indicator, forward one/multiple messages, attachment captions and forwarded indicator; retry deduplication. |
| CHAT-07 | Delete for me across owned devices; delete for everyone through redaction, sync and restore reconciliation. |
| CHAT-08 | Multiple photos, videos and documents, quality selection, preview, captions, per-file progress/cancel/retry and network-specific downloads. |
| CHAT-09 | Voice-note record/lock/pause/resume/discard/preview, waveform, seek, 1/1.5/2x playback and audio focus. |
| CHAT-10 | Static/animated stickers, licensed bundled packs, import, favorites, recents and bounded decoding. |
| CHAT-11 | Local message/media search, unread filter, pin/archive/mute, draft indicators, jump to message, read-receipt/presence preferences. |
| CHAT-12 | Verified device list, QR/security-code verification, new-device notice, revoke, history/read-state sync and key recovery. |
| CHAT-13 | Per-conversation notifications, native inline reply, mark read/mute, locked-screen privacy and precise deep links. |
| CHAT-14 | Manual/scheduled encrypted backups, optional videos, progress/size, cloud account choice, recovery key, restore and device transfer. |
| CHAT-15 | DM claim card and dedicated claim room, live resource authorization, approver replacement, selected E2EE history handover and extensible context types. |

### Voice calls

| ID | Required behavior |
| --- | --- |
| VOICE-01 | Start one-to-one calls from chat, profile, call history or callback notification. |
| VOICE-02 | Group calls, selected invitees, additional invites, join ongoing and rejoin after interruption. |
| VOICE-03 | Incoming calls in foreground/background/locked/system-terminated states, subject to OS admission. |
| VOICE-04 | Answer, decline, cancel, hang up, callback; distinct missed/busy/declined/unavailable/failed outcomes. |
| VOICE-05 | Ring admitted devices; exactly one winning acceptance for a recipient; stop other devices. |
| VOICE-06 | Mute, speaker/earpiece, wired/Bluetooth routing, route changes, proximity and system audio focus. |
| VOICE-07 | Waiting-call reject or end-and-answer, with Telecom/CallKit cellular interruption integration. |
| VOICE-08 | Cross-device history: direction, participants, times, connected duration and termination reason. |
| VOICE-09 | Wi-Fi/mobile handover, reconnect, network quality, adaptive bitrate and data-saving mode. |
| VOICE-10 | Persistent in-app call controls across HRIS navigation plus system mute/hang-up controls. |
| VOICE-11 | E2EE, participant identity/change visibility, key rotation, block/report and DND/Focus respect. |

### Video calls

Video inherits the voice requirements.

| ID | Required behavior |
| --- | --- |
| VIDEO-01 | Start video or request upgrade from voice; recipient consent and camera permission. |
| VIDEO-02 | Front/back and web camera selection, on/off, self preview/mirroring and orientation. |
| VIDEO-03 | One-to-one/group grid, active speaker, pinning, movable self preview and camera-off states. |
| VIDEO-04 | Native PiP and in-app minimized call without creating another media connection. |
| VIDEO-05 | Screen capture and supported system audio; web screen/window/tab and native capture permission. |
| VIDEO-06 | Adaptive resolution, simulcast, bandwidth degradation and retained audio when video stops. |
| VIDEO-07 | Device-processed background/blur, framing and lightweight effects with capability limits. |
| VIDEO-08 | E2EE, consistent media/capture indicators and OS-compliant background capture. |

### Meetings

| ID | Required behavior |
| --- | --- |
| MEET-01 | Instant/scheduled/recurring meetings, title/description/host, timezone, join code/link, reschedule/cancel/remind. |
| MEET-02 | HRIS meeting calendar, ICS, opt-in Google/Outlook integration for app-created/linked events. |
| MEET-03 | Pre-join camera/microphone/speaker selection/test, guest name, connectivity check and initial media selection. |
| MEET-04 | Knock/waiting room, admit/reject, lock, invitation limits and removed-guest handling. |
| MEET-05 | Host/co-host/presenter/participant/viewer, 500 interactive plus 500 viewer capacity, atomic seat/role admission. |
| MEET-06 | Host mute, restrictions on unmute/camera/share/chat, removal, co-host/host transfer, leave/end for all; no forced microphone activation. |
| MEET-07 | Grid, spotlight, active speaker, pin, fullscreen, speaker plus presentation, participant/chat sidebars and adaptive mobile layout. |
| MEET-08 | Raised-hand queue, reactions, speaking indicators and speaking-while-muted notice. |
| MEET-09 | Screen/window/tab and supported audio sharing, presenter changes, pointer, annotation and presentation permissions. |
| MEET-10 | E2EE meeting chat/replies/mentions/files, moderation, post-meeting access; guests have no employee directory/company chat access. |
| MEET-11 | Manual/automatic breakout assignment, transfers, timer, host broadcast/help request and return to main room. |
| MEET-12 | Scheduled/live polls, anonymous option, votes, moderated Q&A, answered marker and results export. |
| MEET-13 | Collaborative whiteboard shapes/text/sticky notes/pointer, undo/redo, snapshots and exports. |
| MEET-14 | Authorized recording start/stop, visible consent/indicator, async processing/playback/download and partial/failed outcomes. |
| MEET-15 | Indonesian/English captions/transcripts, speaker identity, timestamps, corrections, search and export. |
| MEET-16 | ID/EN caption translation, grounded summaries/decisions/action items/catch-up/Q&A; timestamp sources and draft publishing. |
| MEET-17 | Attendance join/leave/rejoin/duration/roles and authorized meeting artifacts/results page. |
| MEET-18 | Companion device for chat/polls/presentation with audio disabled to prevent echo and capacity accounting. |
| MEET-19 | Keyboard, screen readers, captions, labels/touch targets, themes, reduced motion and text scaling. |
| MEET-20 | Reconnect/network change/host recovery, recorder/AI failures and full rooms without displacing existing attendees. |

## Defaults

| Setting | Initial value |
| --- | --- |
| Verified communication devices | 5 per account |
| Group chat | 1,024 members |
| Group call | 32 participants |
| Attachments | 30 per message; 100 MiB per file |
| Edit window | 15 minutes from server acceptance |
| Delete-for-everyone window | 48 hours |
| Ringing | 45 seconds; stale delivery cannot start another call |
| Meeting duration | 24 hours per occurrence |
| Backup | Opt-in, Wi-Fi schedule, videos selected separately |
| Chat retention | Until deletion under conversation policy |
| Recordings | 30 days |
| Transcripts and notes | 90 days |
| Server recording/transcription | Off until an authorized explicit action |

Retention changes require an impact preview; they must not silently destroy
existing records. Backup cannot revoke copies a recipient already exported.
Losing every trusted device and recovery key loses access to encrypted history.

## Claim context

The card carries company, resource kind/ID, and submitted revision. Its title,
amount, status and attachments are acquired from live authorized HRIS APIs.
Forwarding a card confers no permission. Submitted expense evidence remains
distinct from the current unsubmitted draft and the immutable approval journal.
Approved chat participants are not automatically authorized approvers. An
approval still requires its existing reviewed, idempotent HRIS command.

Membership removal gates future server access and rotates future message keys.
Already decrypted information on an offline recipient cannot be recalled. A new
approver receives selected prior messages only through authorized participant
devices, with source/time attribution, never through a server-held recovery key.

## Phases and acceptance

| Phase | Required result |
| --- | --- |
| P0 | Git PRD/ADR/threat model/inventory; actual Android/web E2EE, encrypted calling, Apple binding proof and preliminary capacity experiment. |
| P1 | DM/groups/timeline/offline/delivery/multi-device/verification/search/notifications. |
| P2 | Attachments/voice notes/stickers/edit/forward/delete and claim integration. |
| P3 | Cloud backup/restore, device transfer, wrong key/corruption/quota/lost-device recovery. |
| P4 | Native voice/video, device races, locked/background answer, callback, routing/PiP/reconnect. |
| P5 | Meeting scheduling/guests/lobby/roles/moderation/presentation/chat/capacity. |
| P6 | Breakout/polls/Q&A/whiteboard/companion/attendance/calendar. |
| P7 | Recording/transcription/caption/translation/AI and artifact authorization. |
| P8 | Full load, chaos/recovery, crypto review, accessibility, pilot, verified Starter ports and rollout. |

Required races include lost responses, duplicated/reordered deliveries,
process restart, account/company replacement, approver reassignment during
acquisition, new/revoked devices, edits/deletes/forwards concurrent with restore,
two-device answer, cancel versus answer, stale pushes, network/route changes,
logout/revocation during calls, guest bypass attempts, concurrent seat promotion,
host/breakout changes and partial recorder/AI failure. Tests inspect server
records/objects/diagnostics for E2EE plaintext or key leakage.

Android acceptance uses owned fixtures/emulators and physical devices. iPhone
testing remains waived if the user keeps that instruction; Apple builds and
simulator checks do not establish actual APNs/PushKit/CallKit device acceptance.
Browser inline reply opens the composer; a closed tab cannot capture media in a
service worker. Android Force Stop, offline/powered-off devices and OS restrictions
can prevent real-time delivery. Missed calls reconcile on reconnection.

## Capacity and operation

Acceptance profile: 5,000 employees, 10,000 active chat connections, one large
500-interactive/500-viewer meeting, ten 25-person meetings and 100 concurrent
one-to-one calls. Reserve service participants separately. Desktop/mobile receive
at most 12/6 actively displayed remote videos; include simulcast, screen share,
E2EE and TURN in tests. Run the primary profile for two hours plus reconnect
bursts and node failure. Stub bulk notification providers to avoid real spam;
verify provider delivery separately with explicitly owned recipients/devices.

Targets under documented network conditions: online message visibility p95 <= 1s,
post-admission meeting join p95 <= 5s, finalized captions p95 <= 5s after the speech
segment; no retry duplicates, cross-company leaks or double acceptance winners.
Use mixed ID/EN, accents, noise, names, numbers and overlapping speakers for AI
evaluation. Human review checks grounding and timestamp support.

The current 8-vCPU/~32-GiB development host is not capacity acceptance. A large
room's initial test node is 64 vCPU, 128 GiB and 10 Gbps, with separate load
generators and a spare node. LiveKit self-hosting keeps one room on one node.
Separate the general media pool, database/API/storage and STT/LLM GPU workers.
1,000 receivers averaging 2 Mbps imply approximately 900 GB/hour egress before
overhead/TURN; this is arithmetic, not measured consumption or a price quote.

Operational targets: 99.9% availability, RPO 5 minutes, RTO 1 hour, demonstrated
by restore drills. Use draining, additive migrations, bounded queues, explicit
retry budgets, independent feature flags, and rollback that preserves records
and keys. Production needs stable HTTPS/WSS/Matrix identity/TURN domains, Apple
signing/APNs VoIP, Web Push, Drive/calendar OAuth and iCloud containers. Track
dependency/model licenses and SBOMs. Do not enable 1,000 seats before validation.
