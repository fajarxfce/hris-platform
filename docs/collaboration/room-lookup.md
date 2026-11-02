# Resolve a native chat room hint

GET /api/v1/companies/{companyId}/collaboration/direct-conversations/summary-by-room
accepts one roomId query parameter and returns the existing minimal direct summary
(conversation plus counterpart label). The room ID must begin with ! and contain
2..255 printable ASCII characters. Clients encode it as a query value, not a path
or destination. The configured Matrix server name is never taken from the packet.

This is a native-only participant read. Current account, company membership, chat
permission, MFA/session assurance, service/provider enablement and native client
policy apply. The existing read transaction holds policy/company/membership/account
and native-session guards, validates before acquisition, and validates again before
returning even a not-found or failed lookup. Cookies and a push payload grant no
access. Retained DIRECT ownership can remain readable when the peer is offboarded.

The existing room-scope repository queries the caller's immutable DIRECT binding
with explicit company, participant, server and room filters (at most two rows to
reject ambiguity), followed by one owned summary read. No directory scan, provider
call, device enrollment, room provisioning, send, cursor mutation or migration is
performed. The domain verifies returned room ownership and summary identity/peer
before returning labels. This contract is not removable group/context membership.

[Validation](../measurements/chat-room-lookup-2026-11-02.json) passes on five unchanged
inputs in337.786s (Gradle337s):55 domain cases/13 suites,147 data/1 suite,2 HTTP/1
suite and116 active worker cases/24 suites, with no failures/errors. The unrelated,
opt-in real elapsed export-retention case remains skipped; total worker report117.
Format and architecture checks pass. Initial attempts retain a test-fixture compiler
scope error (25.0s) and an inactive-device SQL fixture guard rejection (202.139s).
Both corrections change tests only. The production database guard was preserved.
Data fixtures prove unpublished/foreign/unknown rooms stay absent, both participants
get their own counterpart, offboarded peer labels remain, invalid hints do no lookup,
room scope cannot be substituted and elapsed authority hides success/not-found.
HTTP fixtures prove encoded reserved characters, no-store/native-only transport,
query validation, company policy, foreign company/participant and revoked session.
The API and worker application contexts must both pass because shared DI changed.
Earlier fixture compilation and inactive-device setup failures are retained.

Mobile RefreshCollaborationList.byRoom uses the same native owner/captured grant,
encrypted list publication mutex and confirmed-denial gate as ordinary reads. Its
companion tests are separate until recorded accepted. Resolution yields metadata;
priority processing still independently admits the current device and validates
its room binding before content access. Encrypted priority hints, notification
read/mute/privacy, actions/replies, provider/OS proof and compatible private rollout
remain. Server chat push stays disabled; all54 communications groups remain open.
