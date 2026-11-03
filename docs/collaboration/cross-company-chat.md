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
There is no public group endpoint or client migration in this increment. A future
repository must bound account pages and company-label aggregation; raw view rows
are not a public API and do not establish native/device/room admission.

[Validation](../measurements/collaboration-space-directory-2026-11-03.json): all 33
core database cases in 16 suites, format/architecture, and 12 recovery-tool cases
pass. Three new actual PostgreSQL cases exercise company switching, committed
last-membership offboarding, private-data exclusion, account replacement, restricted
role ownership and real archive restoration. Recovery accepts schema 117 without
changing any encrypted-envelope format. Live schema remains 116.

## Required replacement

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
