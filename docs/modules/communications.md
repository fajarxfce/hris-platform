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

## Publication and retention policy (planned)

At publication, resolve the current company-local employment date, selected groups/units, account bindings, and active memberships. Only active/probationary employees with an active account and announcement-read permission are recipients. Deduplicate accounts with multiple matching employments. Abort without a partial inbox if the bounded recipient selection exceeds 5,000 or its audience is no longer valid.

Store the recipient snapshot and inbox before attempting an external notification. The publisher's original credential version and current grant must still be valid when the worker runs. Queued content is frozen. Cancelling a queued publication requests worker acknowledgement; returning it to draft requires a terminal failed/cancelled job. A new schedule creates a new retained job request.

Published announcements are ordinary company correspondence. Group removal or a branch move changes future audiences; it does not retract messages already delivered to an account. New members receive no historical inbox automatically. Reading an old message still requires its original recipient account, an active company membership, and current announcement-read permission. Administrative archival withdraws the message from recipient reads and emits a synchronization tombstone. Confidential employee documents use the separate document-access rules.

Read and acknowledgement timestamps are explicit commands with optimistic versions and idempotency. A GET must not mark a notification read. The mobile client opts into the account-owned inbox sync collection; company/account changes and revoked access invalidate its local partition. Push payloads identify an inbox/resource and contain no payroll, document, or personal-data content. Resolving a deep link always performs fresh authorization.
