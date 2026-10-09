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

At publication, resolve the current company-local employment date, selected groups/units, account bindings, and active memberships. Only active/probationary employees with an active account and announcement-read permission are recipients. Deduplicate accounts with multiple matching employments. Abort without a partial inbox if the bounded recipient selection exceeds 5,000 or its audience is no longer valid.

Store the recipient snapshot and inbox before attempting an external notification. The publisher's original credential version and current grant must still be valid when the worker runs. Queued content is frozen. An optional UTC schedule must have microsecond precision, lie within the next 365 days, and remains immutable on its job. Publication uses one bounded transaction for the recipient snapshot, inbox, published revision, fenced job completion, audit, and outbox. A failure exposes no partial publication. Cancelling a queued publication requests worker acknowledgement; returning it to draft requires a terminal failed/cancelled job. A new schedule creates a new retained job request. An announcement permits at most eight publication attempts; no automatic reset starts a new attempt. A successful publication cannot return to draft. Revision limits reserve capacity for publication and archival.

Published announcements are ordinary company correspondence. Group removal or a branch move changes future audiences; it does not retract messages already delivered to an account. New members receive no historical inbox automatically. Reading an old message still requires its original recipient account, an active company membership, and current announcement-read permission. Administrative archival withdraws the message from recipient reads. Withdrawn rows and their immutable publication evidence remain retained; a later read/acknowledgement or command replay cannot restore access. Confidential employee documents use the separate document-access rules.

Read and acknowledgement timestamps are explicit commands with optimistic versions and idempotency. A GET must not mark a notification read. Acknowledgement also marks an unread item read. Repeating an already applied state is a no-op, while an obsolete observed version requires refresh. Native bearer clients use the same endpoints and access checks. Account-owned inbox synchronization is available through explicit `INBOX` selection. Read/acknowledgement produce versioned updates; archival produces a tombstone. [Bounded FCM dispatch](../push.md) is implemented after commit, with live session/access checks, per-device checkpoints, explicit retry/expiry, and version-guarded token retirement. Push payloads identify an inbox/resource without payroll, document, or personal-data content; resolving a deep link requires fresh authorization.

The employee permission catalog includes `announcements.read`. Existing memberships and saved role-template snapshots do not acquire new permissions automatically; administrators must grant or explicitly reapply the intended access.
