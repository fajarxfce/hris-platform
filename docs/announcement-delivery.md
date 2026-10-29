# Announcement push delivery

Administrators open **Push delivery** from the current announcement. The screen
shows its retained publication recipient total, current dispatch states and device
processing counts. Refresh is explicit. Inbox correspondence remains available
when push cannot be sent; this view does not retry, republish or acknowledge it.
English and Indonesian copy use the selected company timezone.

`GET /api/v1/companies/{companyId}/announcements/{id}/delivery` requires both the
original and current `announcements.manage` grant. Announcement, company,
membership and account guards protect current resource/credential access. Recent
MFA is checked after guard acquisition and again after the aggregate is read.
A foreign announcement is not found; ordinary Inbox permission grants no access.
The read creates no business, audit, outbox or idempotency mutation.

| Field | Meaning |
| --- | --- |
| `announcementId`, `recipientCount` | Current announcement and its immutable publication recipient total; drafts have zero recipients. |
| `pending`, `processing` | Recipient dispatches awaiting work or holding a worker lease. |
| `completed`, `failed`, `superseded` | Terminal recipient dispatches. Completed processing can include no eligible devices. |
| `unavailableRecipients` | Publication recipients without a retained dispatch record, including older/untracked correspondence. |
| `processedDevices`, `acceptedDevices`, `rejectedDevices` | Device checkpoints in the retained dispatches; accepted/rejected are subsets of processed. |
| `observedAt` | Authorized server observation time. |

The five dispatch states plus unavailable recipients equal the publication total.
These are recipient dispatch counts, not delivery confirmations. Device counters
can exceed recipient counts because one recipient can have multiple sessions.
Acceptance by the push service does not establish receipt or display on a device.
Terminal operational history is eligible for cleanup after seven days. Its removal
decreases the tracked counts and increases unavailable recipients; it does not
invent success or remove the original publication/Inbox evidence.

V101 captures only company/announcement/Inbox identifiers, state and three device
counters in `announcement_push_delivery`. Existing raw recipient/session dispatch
RLS stays unchanged. A fixed, trigger-only function maintains the projection in
the same transaction as dispatch insertion, worker progress or cleanup. Its search
path and source table are fixed and direct execution is revoked. Application and
worker roles cannot write the projection directly. Forced RLS requires current
company membership and management access for reads. No token, recipient, session,
provider payload or free-form failure detail appears in the HTTP response.

Upgrade locks the raw queue against concurrent writers until backfill and trigger
installation commit. It preserves the publication, revisions and Inbox rows. The
aggregate reads at most 5,001 rows to detect a violation of the 5,000-recipient
limit. Every dispatch has at most 500 processed devices; repository and dashboard
validation reject inconsistent totals. One SQL statement provides the operational
snapshot; it can become stale as the worker advances.

Each dashboard observation belongs to one account/company/announcement. Refresh,
visibility loss, permission loss or replacement cancels the old request and clears
its counts. Foreground loading waits for workspace identity revalidation rather
than racing another request. Late results cannot restore an abandoned view. There
is no automatic polling and no retained cross-account cache.

## Validation

On 29 October 2026, 117 API, 94 worker and three repository tests passed. The
manual one-hour export test was skipped in this worker suite and remains owned
by the separate checkout. All 3,974 dashboard tests (349 files), TypeScript,
Biome, architecture/formatting and production builds passed. Thirteen distinct
Chromium scenarios passed across focused runs, including hidden/foreground
recovery, revoked access, stale company responses and Indonesian narrow layout.
A browser finding of duplicate foreground reads was fixed before the final run.

Actual API/PostgreSQL/worker/browser acceptance passed in 45.5 seconds (1.8 minutes
with setup), preserving Inbox evidence and creating no delivery-read audit writes.
The V100→101 test held a dispatch update open, observed the migration waiting,
committed it and verified exact backfill, unchanged correspondence, new publication
capture and cleanup. Schema-101 recovery restored the exact delivery and account
audit projections, forced RLS and revoked trigger execution, alongside 104 objects
(2,098,795 bytes), two encrypted records and both retained key generations. Twenty
recovery-tool regressions passed. Owned fixtures were removed. See the
[measurement record](measurements/announcement-delivery-2026-10-29.json) and
[agent handoff](agent-handoff.md).
The browser/API acceptance publishes real correspondence and uses explicit owned
SQL fixtures to display terminal failure and cleanup. Those fixtures do not prove
elapsed seven-day retention or actual FCM delivery. Worker integration covers real
claim/checkpoint/acknowledgement and cleanup through the configured test provider.
[Native Firebase acceptance](native-push-acceptance.md) tracks actual SDK registration;
[actual FCM delivery](native-fcm-acceptance.md) subsequently passed using the supplied
server credential, with foreground/background Android notifications and unchanged Inbox state.
