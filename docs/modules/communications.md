# Communications

Own announcement drafts/schedules/publication/audience, acknowledgement, server inbox, channel delivery, and device registrations.

Scope audiences by permitted company/branch/department/groups. Persist inbox before external delivery. Fan-out via transactional outbox; use stable notification and recipient IDs. FCM/email failures do not erase inbox.

Notification deep links identify resources and require fresh authorization. Sensitive payroll amounts are not sent in push content. Revalidate recipient access when needed; unregister revoked devices.

Screens: announcements, audience preview, scheduling, inbox, delivery failures.

Acceptance: duplicate events, scheduled audience resolution, denied push permission, invalid token, access changes, and idempotent acknowledgement.

## Authoring and audience contract

Announcements contain a plain-text title/body and an acknowledgement requirement. Lists and history return summaries; a detail request retrieves one complete revision. Administrative access requires `announcements.manage` in the current company. All edits retain immutable history and require an observed version and idempotency key. The limits are 200 characters for a title, 16,000 for a body, and 1,000 retained revisions per announcement.

An audience selects exactly one kind: the whole company, selected branches, selected departments, or named groups. A non-company audience contains 1–32 distinct references, combined with OR. An empty selection never means the whole company. References must belong to the same company and be active when a new draft is saved or publication is requested.

Named groups contain explicit employment IDs, rather than person or account IDs. Up to 128 groups per company and 5,000 distinct members per group are supported. Group definitions and membership lists are versioned together, with up to 1,000 retained revisions per group. Archival is an inactive revision, not deletion of historical evidence.

## Audience preview

Administrators can preview a saved draft or queued announcement using its observed version. The response contains the current eligible recipient count, evaluation instant, company-local date, and referenced group/unit versions. It contains no recipient identities and creates no job, receipt, or audit mutation. Empty audiences return zero; selections exceeding 5,000 fail explicitly.

A preview does not reserve future membership. Scheduled publication evaluates current eligibility again when its worker executes. Published/archived messages expose their retained publication count instead of a new preview. The preview requires current management access, including after pending guards.

## Publication and retention policy

`GET /announcements/{id}/publication-review` returns current content, minimal linked
job status, and available actions in the selected company. Management permission
does not grant general job access or expose job inputs/credentials. Stopped jobs
can expose recovery; cancellation requests alone do not permit reset or archival.
The response respects revision/attempt limits and current MFA. Recipient preview
remains a separate read, and commands revalidate the reviewed state.

At publication, resolve the current company-local employment date, selected groups/units, account bindings, and active memberships. Only active/probationary employees with an active account and announcement-read permission are recipients. Deduplicate accounts with multiple matching employments. Abort without a partial inbox if the bounded recipient selection exceeds 5,000 or its audience is no longer valid.

Store the recipient snapshot and inbox before attempting an external notification. The publisher's original credential version and current grant must still be valid when the worker runs. Queued content is frozen. An optional UTC schedule must have microsecond precision, lie within the next 365 days, and remains immutable on its job. Publication uses one bounded transaction for the recipient snapshot, inbox, published revision, fenced job completion, audit, and outbox. A failure exposes no partial publication. Cancelling a queued publication requests worker acknowledgement; returning it to draft requires a terminal failed/cancelled job. A new schedule creates a new retained job request. An announcement permits at most eight publication attempts; no automatic reset starts a new attempt. A successful publication cannot return to draft. Revision limits reserve capacity for publication and archival.

Published announcements are ordinary company correspondence. Group removal or a branch move changes future audiences; it does not retract messages already delivered to an account. New members receive no historical inbox automatically. Reading an old message still requires its original recipient account, an active company membership, and current announcement-read permission. Administrative archival withdraws the message from recipient reads. Withdrawn rows and their immutable publication evidence remain retained; a later read/acknowledgement or command replay cannot restore access. Confidential employee documents use the separate document-access rules.

Read and acknowledgement timestamps are explicit commands with optimistic versions and idempotency. A GET must not mark a notification read. Acknowledgement also marks an unread item read. Repeating an already applied state is a no-op, while an obsolete observed version requires refresh. Native bearer clients use the same endpoints and access checks. Account-owned inbox synchronization is available through explicit `INBOX` selection. Read/acknowledgement produce versioned updates; archival produces a tombstone. [Bounded FCM dispatch](../push.md) is implemented after commit, with live session/access checks, per-device checkpoints, explicit retry/expiry, and version-guarded token retirement. Push payloads identify an inbox/resource without payroll, document, or personal-data content; resolving a deep link requires fresh authorization.

The employee permission catalog includes `announcements.read`. Existing memberships and saved role-template snapshots do not acquire new permissions automatically; administrators must grant or explicitly reapply the intended access.

## Dashboard review

The announcements directory, current message, immutable revision history, and
publication-job link are available under Communications. Reads require current
management access and retain only one bounded page. Account/company replacement
cancels the previous request and clears its content; a late result cannot restore
it. Messages are rendered as plain text. Dates use the selected company timezone,
with English and Indonesian copy and responsive light/dark layouts.

Draft creation and editing support bounded named audience selection, acknowledgements,
and revision reasons. Current-company reference searches and selected-label lookups
retain one page. A failed lookup preserves the draft. Pending and ambiguous saves
retain one immutable operation and payload; an explicit retry can recover the original
receipt. A stale revision requires reviewing current data before editing. Departure
protection and MFA renewal retain the mounted form without silently replaying a save.

Audience group directories, creation, revision review, editing and activation are
implemented. Member selection uses employment references, with at most 50 labels
loaded per page and 5,000 retained IDs per definition. Current labels do not rewrite
historical membership. Response loss retains the original command, while a definite
version conflict requires explicit review before another edit.

Publication review displays current server-authorized actions and minimal job status.
Preview is explicit. Publishing supports immediate delivery or an unambiguous time in
the company timezone. Publish, return to draft, and archive each require a reason and
confirmation; command controllers retain their observed version and operation through
response loss and MFA renewal. Definitive conflicts require a new review and preview.
Cancellation uses the existing job workflow; a worker must confirm a terminal status
before recovery. Review refreshes are explicit and do not poll automatically.

The mobile inbox currently synchronizes and reads encrypted correspondence;
read/acknowledgement commands and push navigation remain separate work.

## Audience references

The company-scoped `communications/audience-references` endpoint discovers
branches, departments, named groups, and employments with `announcements.manage`.
It exposes only IDs, labels/codes, resource versions, and definition activation.
Employment references include the employee number and name without profiles,
account bindings, or contact details. Group membership may include an existing
employment whose start date is still in the future; publication evaluates its
eligibility separately. `active` is therefore null for employment references.

Search is literal and case-insensitive, bounded to 120 characters and 50 entries
by default (200 maximum), with ascending UUID pagination. Up to 50 selected IDs
can be resolved in one request, including inactive saved definitions. Selected-ID
lookup cannot be combined with text search or a cursor; foreign/missing IDs are
omitted. Each page is one SQL snapshot under company RLS. Access and current MFA
are rechecked after access guards. These labels do not reserve a recipient or
authorize later saves, previews, or publication.
