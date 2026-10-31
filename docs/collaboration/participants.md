# Collaboration participant discovery

`GET /api/v1/companies/{companyId}/collaboration/participants` is a native-only
contact selector for starting employee conversations. It requires the native
bearer, positive Android/iOS client build and `collaboration.chat`, with both
collaboration/provider configuration enabled. It does not contact Matrix or
require the caller's provider enrollment to have finished.

Parameters are `query` (optional literal display-name text, at most 80 UTF-16
units, no control characters), `after` (optional account UUID) and `limit`
(default 30, maximum 50). The response is `items: [{accountId, displayName}]` and
`nextCursor`, with no-store. Account ID is distinct from person/employment/device
IDs. No email, profile, provider credential, membership permission list or private
key is acquired for this projection.

The SQL selector reads active accounts and active memberships in the selected
company with explicit chat permission. It excludes the caller before applying
UUID keyset pagination and finite overfetch. Literal `%` does not become a search
wildcard. Existing approval/lifecycle selectors retain their behavior because
account exclusion is optional and defaults to none in the shared repository.

`ListNativeCollaborationParticipants` owns the transaction and guards company
policy, company, memberships, account and the authenticated native parent in that
order. It verifies current actor/credential/permission/native-version/access/MFA
and minimum-build/maintenance/module policy before acquisition and at completion.
Expired access/MFA or a scheduled policy transition withholds an already-read
page. Revoked or replaced parent sessions are rejected before peer acquisition.
The feature allocates no communication device, queue job, provider login or key.

This is a live selection hint, not a room permission or an immutable membership
snapshot. A conversation command must authorize each selected account under its
own current guards; a contact becoming ineligible cannot be admitted from an old
selector response. Clients reset pagination when query/company/account changes.
Browser-parent admission, conversation controls, SDK sync and the chat UI remain
separate work. CHAT-01 is in progress, not accepted.

[Executed evidence](../measurements/collaboration-participants-2026-10-31.json)
records actual PostgreSQL isolation/pagination/completion checks, native HTTP
response/privacy/transport checks, and existing approval/lifecycle completion
regressions. Shared API/worker composition is verified with each changed graph.
