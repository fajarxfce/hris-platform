# Cross-company employee conversations

The user requested this amendment on3 November2026. It is required work under
CHAT-01, not accepted implementation. The deployment serves one organization
group, so contact discovery spans its companies; guests remain meeting-only.

## Current mismatch

`ListNativeCollaborationParticipants` reads active company memberships with
`collaboration.chat`. The private demo originally had two such accounts, which explained why the user
saw only Administrator. Four fictional SAMPLE employees now have linked, active
chat accounts: provisioned through normal membership/profile APIs and verified
with native logins (see the
[demo evidence](../measurements/chat-demo-accounts-2026-11-03.json)). Their temporary verification sessions were revoked. This
fixes demo directory eligibility; it does not establish Firebase sign-in for those
fictional accounts or implement cross-company conversations.

`OpenNativeCollaborationDirect` revalidates both accounts against the selected
company. Room pairs, Matrix identities, credentials, push bindings and encrypted
mobile partitions carry that company. These are real admission/ownership contracts;
changing discovery alone would publish contacts that cannot open a usable room.

## Database foundation — 3 November 2026

V117 creates one immutable generated communication-space identity per installation.
Two security-barrier views admit the current active account through chat permission
in any active company and expose only account ID/name and public company labels.
The non-login owner has column-only source privileges, with role-specific RLS;
application roles cannot assume it. HR/resource RLS and existing company-bound
provider identities, keys, devices, grants and Google/OIDC bindings are unchanged.
There is no public group endpoint or client migration in this increment. The
directory repository now pages at most 50 accounts before acquiring at most eight
public company labels per account, with explicit label overflow. Its single SQL
snapshot bounds raw overfetch to 51 accounts times nine labels and searches literal
display-name text; login email is excluded. Raw view rows do not establish
native/device/room admission. [Repository evidence](../measurements/collaboration-space-contact-page-2026-11-03.json)
records three passing cases in76.781s with3583 unchanged source hashes, including
actual PostgreSQL pagination, company switching, committed offboarding/revocation,
search escaping, invalid bounds, technical failure mapping and interruption.

[Validation](../measurements/collaboration-space-directory-2026-11-03.json): all 33
core database cases in 16 suites, format/architecture, and 12 recovery-tool cases
pass. Three new actual PostgreSQL cases exercise company switching, committed
last-membership offboarding, private-data exclusion, account replacement, restricted
role ownership and real archive restoration. Recovery accepts schema 117 without
changing any encrypted-envelope format. Live schema remains 116.

## Required replacement

V118 now exposes an owned legacy provider metadata view to support explicit
migration. It retains original company/device IDs, Matrix user/device IDs and
retirement/version metadata, without reading token ciphertext, native credentials,
gateway replays or login email. The restricted non-login owner cannot access those
columns and cannot be assumed by the application role. Eligibility in a current
company is required, while the original HR company and native binding need not
remain active. Retired references are inventory, never permission to resume them.
No original row is rewritten, new identity selected or SDK store relocated.
[Evidence](../measurements/collaboration-legacy-identity-inventory-2026-11-03.json)
records actual PostgreSQL migration/isolation/restore,36 core cases in211.587s,
format/architecture and12 recovery-tool cases. Recovery accepts schema118 with
the same authenticated envelope formats; live schema remains116.

The owned inventory repository now uses a bounded compound company/device cursor,
at most50 records plus one overfetch. Mapping validates original Matrix identity,
account/space, lifecycle metadata and strict cursor progression. An actual
PostgreSQL case traverses nine devices in five pages across unsigned UUID boundaries;
two boundary cases exercise invalid input, driver failure, cancellation and foreign
metadata. [Evidence](../measurements/collaboration-legacy-device-pages-2026-11-03.json):
45.064s/3 cases,3597 unchanged source hashes, format/architecture PASS. It is not
wired to DI or HTTP and does not select an identity automatically.

Mobile's current crypto-store ownership contains the original company and native
parent. A future group owner must explicitly retain that store reference and verify
the provider user/device match; changing its company component can open a different
empty store. The inventory supplies server-side identity facts, not client keys or
proof that a local store exists. Multiple historical identities must stay explicit;
do not silently merge/rekey rooms or infer a replacement from the selected company.

The initial native contact use case now accepts an account-scoped actor, reads
current native/account state under account-then-session guards, and rechecks
security and eligible-space projection after acquiring the contact page. A global
administration grant is unnecessary and cannot replace group eligibility. The
short observation retains exact native parent, proof, credential and version,
clips access/hard/MFA expiry, and grants no device or room authority. Eleven domain
cases pass in27.681s; [evidence](../measurements/collaboration-space-native-admission-2026-11-03.json)
distinguishes these controlled fake-repository cases from actual PostgreSQL reads.

Organization chat availability has its own immutable policy value, disabled by
default, with independent Android/iOS minimum builds. The selected HR company's
maintenance/module controls continue to govern its HR resources and existing
company endpoints. The new group policy is not yet bound to configuration/DI;
the use case is not exposed over HTTP or consumed by Mobile. Complete the group
device/room contract before publishing reachable contacts. Public directory reads
use fresh statement snapshots; they do not reserve peer eligibility for a future
provider call. Mutations and exchanges need their own current admission and race
acceptance, without taking company/member locks after account/session locks.

Organization communication admission must be independent of HR company-resource
admission. Use a stable account identity within the installation's communication
space, and explicit conversation memberships. An active account with eligible
employment/communication access in at least one company can contact another
eligible account in the group. No synthetic HR membership or HR permission is
granted merely because two people chat.

Expose a bounded contact projection: stable account/contact identity, display name,
and public company labels. Search supports names and a deliberately published work
contact identifier; do not expose personal login email by default. Employee rows
without an eligible account explain that chat access is unavailable instead of
silently pretending that the person can receive messages. Opening a room rechecks
current sender/recipient eligibility, block policy and session assurance.

The client owns the organization inbox by audience/account, independent of the
selected HR company. Business context cards keep their original company/resource
and require live authorization when opened. A company switch retains chat drafts,
history and the active communication runtime; account replacement, device revocation
or confirmed communication denial closes the affected owners. Offboarding from one
company does not remove group chat when another eligible employment remains.

## Migration and acceptance order

1. Introduce communication-space identities, admission and member projections with
   narrowly scoped RLS. Prove no company-profile/resource disclosure or cross-account
   access, including concurrent offboarding and session revocation.
2. Provide versioned group contact, direct-room and device endpoints. Preserve old
   endpoints during migration and canonical pair idempotency across both callers.
3. Map existing company-bound rooms/provider identities explicitly. Never regenerate
   Matrix identities/keys or silently re-encrypt existing history. Old rooms and
   pending commands keep their original ownership until a reviewed mapping exists.
4. Migrate mobile local partitions, runtime ownership, incoming/outgoing workers,
   push routing, read receipts and notification deep links together. Prove process
   death, offline drafts, lost responses and account changes preserve the right data.
5. Exercise two fictional employees from different companies end to end: discover,
   encrypted send/receive, background notification, inline reply, switch HR company,
   reopen offline, then revoke communication eligibility. Update Android, Apple and
   web acceptance before claiming CHAT-01 complete.

The current UI increment moves Chat into bottom navigation and repairs presentation
and restored pagination. It must not be described as accepting this migration.
