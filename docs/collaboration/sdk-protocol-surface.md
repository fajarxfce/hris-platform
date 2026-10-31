# Observed SDK protocol surface

The actual pinned Android/browser experiment now records bounded Synapse request
counters alongside its encryption/restart/media assertions. Only method, static
servlet class and integer count are retained. Account/room/device IDs, paths,
query strings, tokens, public/private keys and message bodies are not collected.
The metrics endpoint belongs only to the isolated loopback provider fixture.

The browser-only baseline used matrix-js-sdk 45.0.0 with Rust crypto. Its one
scenario passed in 6.772s (15.512s owned run). The combined Android 35/browser
experiment reused the source-matching Kotlin SDK 26.10.23 debug fixture APK;
two scenarios passed in 18.498s (39.009s owned run), with complete cleanup.
Fifteen ownership/metrics driver cases pass. These are protocol observations,
not product or capacity acceptance. The physical phone and live HRIS were untouched.

The Android fixture explicitly restores SlidingSyncVersion.NATIVE, then owns a
SyncService. The combined run made eighteen POST requests to SlidingSyncRestServlet;
the browser baseline made none. Pinned Synapse describes this as MSC4186 Simplified
Sliding Sync, historically derived from MSC3575. Do not implement only classic
GET sync and assume the existing native fixture will work through that gateway.
The pinned Kotlin binding also exposes syncV2/syncOnceV2; those alternatives are
not exercised by the current fixture and must not inherit its acceptance.

| Observed operations | Integration implication |
| --- | --- |
| Classic SyncRestServlet and native SlidingSyncRestServlet | Separate request/response/cursor semantics, bounded windows, admission and cancellation. |
| KeyUploadServlet, KeyQueryServlet, OneTimeKeyServlet, SendToDeviceRestServlet | Own-device upload and currently admitted recipient/device scope; participant private keys remain client-owned. |
| RoomMemberList, RoomMessageList, RoomEvent, RoomEventContext | Explicit room/history admission and bounded reads, including invitation history and restart acquisition. |
| AccountData, filters, push rules and capabilities | Own-user state only; distinguish required SDK behavior from optional discovery. |
| Versions, room-key backup and VoIP discovery | A request count does not establish success or feature acceptance; unsupported discovery must not silently enable backup/call capabilities. |
| Room create/join and timeline send | Product room creation stays under HRIS controls; only admitted bindings may reach client join/encrypted send. |
| Login/register/health/OPTIONS | Includes fixture setup and negative/control probes; these counts do not authorize public provider login or registration. |

Counters aggregate the SDK scenarios and explicit fixture control requests. They
are not per-client traces, status-code results, a complete endpoint inventory or
an automatic allowlist. The gateway must continue to deny every unimplemented
operation. In particular, unpublished/partial rooms must not reach a client's
sync view before their encryption/security state has been verified. Native list
operations, room subscriptions and E2EE/to-device extensions need their own scoped
response tests; filtering only ordinary timeline arrays is insufficient.

[Evidence](../measurements/collaboration-sdk-protocol-surface-2026-10-31.json)
retains exact driver/APK hashes, both sanitized results and counters. The bounded
parser rejects unexpected labels, malformed/noninteger/excessive counters and
oversize input, and closes the HTTP owner on parsing failure. Raw metrics are not
written to the retained evidence. Keep long-poll/media capacity separate from the
short identity/control exchanges when implementing product transport.
