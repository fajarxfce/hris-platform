# Communications requirement review — 30 October 2026

The five initial requirements and eight original detailed sections, plus the new
optional-email section, have been reviewed against source and matching behavior.
Local and final-source CI acceptance are complete after adding email delivery
and correcting read completion. This review covers Communications and its existing native evidence;
the remaining Platform modules and full Mobile inventory remain open.

| Requirement | Implementation and matching behavior |
| --- | --- |
| `communications.01`: drafts, schedules, audiences, publication, Inbox, acknowledgement, channels and devices | Versioned authoring/group policies, `AdvanceAnnouncementPublication`, explicit Inbox commands and session-owned push registration. Authoring/publication/Inbox API suites, real browser/worker workflow, and native registration evidence. |
| `communications.02`: scoped audiences, atomic Inbox before delivery, stable identities, independent FCM/email failure | Publication resolves current employment, group, account and membership under scoped guards; one bounded transaction captures immutable content/recipients, Inbox, dispatch, job, audit and outbox. Publication integrity/capacity tests exercise rollback and 5,000 recipients. Push/email worker tests exercise provider rejection, response loss and fenced retry without erasing correspondence. |
| `communications.03`: authorized links, minimal payloads, current recipients and revoked devices | `DeliverInboxPush`/`DeliverInboxEmail`, live native-family checks and observed-version token retirement. Worker tests reject withdrawn/revoked targets and protect replacement tokens. Actual Android FCM and HTTPS-link evidence verifies navigation and unchanged read/ack state. |
| `communications.04`: announcement, preview, schedule, Inbox and failure views | Dashboard Communications routes/controllers and Mobile encrypted Inbox. Prior 4,006-case dashboard acceptance includes authoring and delivery, with fifteen distinct related Chromium scenarios; current actual browser/worker workflow exercises editing, lost-response recovery, preview, publication, channel observations, native Inbox and archival. |
| `communications.05`: duplicates, scheduled resolution, denied permission, invalid token, access changes and acknowledgement replay | Publication uses current audience rather than preview reservation; duplicate/failure/concurrency suites retain one immutable outcome. Native registration/permission lifecycle and worker invalid-token tests cover device failures. Explicit optimistic read/ack tests preserve owner and operation identity; the new thirteen read-completion cases reject expired MFA after acquisition. |

Detailed review retains finite title/body/revision, audience and group limits;
count-only previews; scheduled UTC admission and bounded publication attempts;
ordinary-correspondence retention and withdrawal; account/company lifecycle in
controllers; minimal reference labels with separate selected-ID lookup; an unread
SQL aggregate independent of loaded pages; actual Android registration/provider
evidence; and minimal administrative push/email projections. Provider acceptance
is distinct from receipt on a device or in a mailbox. Operational cleanup never
removes retained Inbox evidence or creates historical recipients.

Evidence: [email](measurements/announcement-email-2026-10-30.json),
[read completion](measurements/communications-read-assurance-2026-10-30.json),
[native registration](native-push-acceptance.md),
[actual FCM](native-fcm-acceptance.md) and
[HTTPS navigation](measurements/app-links-2026-10-29.json).
The email source passed all ten full-CI jobs. Read completion adds 106 API,
13 domain and three repository cases, package/quality checks and the actual
API/worker/browser/SMTP workflow. Final source `8e5dd3db` passed all ten CI jobs.
Physical iPhone testing stays explicitly waived; these observations do not claim
actual APNs or an Apple device login.

Final source `8e5dd3db49b2568917c44432574f58ce1eae6dae` passed all ten jobs in
[CI 41013958569](https://github.com/fajarxfce/hris-platform/actions/runs/41013958569),
including all backend/dashboard shards, real integration and recovery.
