# Communications

Own announcement drafts/schedules/publication/audience, acknowledgement, server inbox, channel delivery, and device registrations.

Scope audiences by permitted company/branch/department/groups. Persist inbox before external delivery. Fan-out via transactional outbox; use stable notification and recipient IDs. FCM/email failures do not erase inbox.

Notification deep links identify resources and require fresh authorization. Sensitive payroll amounts are not sent in push content. Revalidate recipient access when needed; unregister revoked devices.

Screens: announcements, audience preview, scheduling, inbox, delivery failures.

Acceptance: duplicate events, scheduled audience resolution, denied push permission, invalid token, access changes, and idempotent acknowledgement.
