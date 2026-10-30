# Announcement email reminders

Authors can select **Send an email reminder** in an announcement draft. The default
is off. Publication creates one durable email dispatch for each captured Inbox
recipient, in the same transaction as Inbox and publication evidence. A failed or
replayed publication cannot create a partial or duplicate queue. Existing Inbox
records are never backfilled by migration V102.

`PUT /api/v1/companies/{companyId}/announcements/{id}` accepts the optional nullable
boolean `emailNotification`. Omission/null preserves an existing draft's choice,
or defaults to false when creating it. Explicit true/false belongs to the command
fingerprint; omission retains the previous fingerprint so pre-upgrade receipts
still replay. Details and exact revisions expose the choice. Queuing freezes it
with the reviewed content through publication, recovery, return to draft and
archival; a later draft edit can explicitly change it.

The Communications worker uses `core/mail` through `MailRepository`, sharing SMTP
configuration with invitation/recovery mail. Configure [outbound email](mail.md),
`HRIS_PUBLIC_URL` and `HRIS_EMAIL_APP_ENVIRONMENT` (`prod`, `dev` or `staging`,
default `prod`). The trusted origin is validated at startup. No request Host or
client-provided URL generates email links. API and worker Compose services pass
the same settings. Disabled SMTP does not prevent publication or erase Inbox.

The worker reads the current recipient account only after shared announcement,
company, membership and account guards, then verifies a live dispatch lease.
Account, company, membership and `announcements.read` must remain active, and the
captured Inbox item must not be withdrawn. Background delivery does not require
a recent interactive MFA session. Denied or archived items stop without another
provider call. Provider I/O occurs outside database transactions. Revocation after
preparation can race an external send; the generic message contains no title,
body, payroll data or credential. Its link independently enforces current access:

`{HRIS_PUBLIC_URL}/app/{environment}/accounts/{accountId}/companies/{companyId}/inbox/{inboxId}`

The Inbox UUID is the stable SMTP Message-ID. SMTP acceptance followed by a lost
acknowledgement may produce a duplicate with that ID. This is not an exactly-once
transport or proof of mailbox delivery. The email never marks Inbox read or
acknowledged. Addresses are not persisted in dispatch rows or delivery observations.

## Bounded ownership and recovery

Two active deliveries per process use an executor with no waiting queue and a
60-second deadline. Database claims are limited to four per call and sixteen live
leases across the deployment. Two-minute leases have owner/token/expiry fences;
expired owners cannot acknowledge or change another owner's progress. Tasks,
polling and deadline timers stop on shutdown. An interrupted send is recoverable
after its lease expires. SMTP sockets also retain their transport timeouts.

Transient failures retry with persisted delays from five seconds to five minutes
and at most eight claims, within a 24-hour enqueue lifetime. Permanent rejection
stops immediately. Crash-only exhaustion is also terminal. Queue completion and
its minimal audit record commit together. Lease/admission failures cannot claim
provider acceptance. Cleanup runs even when SMTP is disabled: it examines bounded
batches of 100 and purges terminal operational rows after seven days. The minimal
administrative projection is removed in the same transaction; Inbox remains.

## Administrative observations

**Notification delivery** retains the push counters and adds nullable `email` to
`GET /api/v1/companies/{companyId}/announcements/{id}/delivery`. Null means the
announcement did not request email. Otherwise it contains `pending`, `processing`,
`accepted`, `failed`, `superseded` and `unavailableRecipients`. These sum to the
retained publication recipient total. Accepted means the SMTP server accepted the
message; it does not mean the mailbox received it or the employee read it.

The endpoint keeps existing live management/MFA checks. Each channel aggregate
reads at most 5,001 minimal rows and rejects a violated 5,000-recipient bound.
Push and email observations can advance independently. V102's forced-RLS
projection exposes no account, address, lease, provider payload or free-form error.
Only a fixed trigger can mutate it. Raw queue access stays private to its recipient
and worker capability. Direct historical dispatch insertion is prohibited.

The dashboard explicitly refreshes observations and clears them on company,
account, permission or visibility changes. Late results cannot restore another
scope. Draft retry preserves the same email selection and operation identity.
English and Indonesian views include the draft choice, exact-revision detail,
SMTP acceptance explanation and unavailable operational history.

Validation and measured limitations are recorded in the
[feature audit](feature-audit-2026-10-30.md). All email acceptance uses owned
fictional recipients and loopback SMTP; no live SMTP provider is configured by
this work. Physical iPhone testing remains waived, and email implementation alone
does not complete the remaining PRD audit.
