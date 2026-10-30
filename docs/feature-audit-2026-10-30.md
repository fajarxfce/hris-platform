# Feature completion audit — 30 October 2026

The user asked whether **all features** are finished and instructed continued work
where they are not. Completion is reopened against the product specification,
each of its eleven module contracts, Mobile feature behavior and the applicable
Starter foundation. Previous implementation and CI records are useful evidence,
but do not prove an unreviewed or missing capability. The active goal is not
limited to the distribution audit completed yesterday.

The [inventory](measurements/feature-audit-2026-10-30.json) preserves exact initial
module requirements and source hashes, plus detailed section names. Its unverified
rows are not failed features; they await source and matching behavior review.
Do not mark the goal complete until that review covers the full scope. Physical
iPhone testing remains explicitly waived. Keep the accepted Google binding,
installed phone, private keys and running development stack intact.

## Confirmed gap: announcement email

The Communications PRD owns channel delivery and explicitly mentions FCM/email.
At the start of this audit, the application published durable Inbox records and dispatched device FCM
hints. Its domain dependencies and DI had no MailRepository integration; the
existing SMTP sender/queue serves only identity invitations and recovery. No
announcement email delivery, channel selection or email delivery observation is
implemented at that point. This was an implementation gap, not missing live SMTP credentials.

Implement explicit per-announcement email selection, retain it in immutable
publication evidence, and queue one stable recipient delivery in the same
publication transaction. Reuse the existing SMTP transport through its domain
contract. Worker delivery must be bounded, own its lease/deadline, revalidate the
recipient and preserve Inbox through rejection, interruption, response loss,
revocation and exhausted retry. Publication replay must not duplicate dispatch.
Extend authoring/review and administrative delivery observations without exposing
email addresses or provider content. Test both API and worker contexts and use
owned fictional SMTP fixtures; do not activate real mail or send to real users.

Continue the remaining module and Mobile audit after this gap; finding one missing
feature does not prove all others complete. HRIS policy stays in Platform/Mobile.
Port only reusable corrections after they are demonstrated downstream.


## Email implementation — local acceptance passed

[Email reminders](announcement-email.md) now implement V102, immutable channel
selection, atomic capture, fenced delivery/maintenance, SMTP transport,
administrative aggregates and the dashboard path. The initial request with
`emailNotification=true` reproduced HTTP 400 before the change.

Acceptance passed 65 announcement/Inbox API tests and 67 OpenAPI tests, 104 worker
tests, 13 communication domain and three repository tests. The one-hour report
retention test is explicitly excluded here and retains its earlier separate
elapsed-time acceptance. All 4,006 dashboard tests, type and architecture checks
passed. Fifteen distinct Chromium scenarios include editor/replay/publication,
revocation, foreground recovery and Indonesian narrow layout. A duplicate
retention notice was fixed before the final delivery run. Real API/PostgreSQL/
worker/browser/SMTP acceptance passed in 44.1 seconds (1.3 minutes with setup).

An independent schema-102 database/content/key recovery restored exact email
selection, raw queue and minimal projection, forced RLS and revoked function
entry, alongside the prior push/account audit records and 104 objects. Twenty-one
recovery regressions passed. The recovery verifier bound was extended only after
reviewing the new nonsecret fields. Full branch CI follows the implementation
commit. The [measurement record](measurements/announcement-email-2026-10-30.json)
retains suite counts, findings and practical limits.

The private API remains at schema 101, SMTP disabled, with its accepted Google
binding and installed phone intact. Its dashboard now serves baseline assets from
an independent immutable directory: local test builds no longer change that live
preview. Both endpoints returned HTTP 200 and the dashboard index matched the
baseline snapshot. This isolation is recorded in the agent handoff.

Continue the full module/Mobile review. Closing the email gap does not establish
complete acceptance for the unreviewed inventory rows.

## Shared approval history — local acceptance passed

The [approval history audit](approval-history-audit-2026-10-30.md) confirms a missing
reader/view for retained decisions and reassignments. Existing shared approval
details show current assignments only. [Implementation](approval-history.md) and [local acceptance](measurements/approval-history-2026-10-30.json)
now cover the guarded history reader, versioned pagination, actual immutable facts,
localized dashboard, lifecycle and real approval workflows. All 118 selected
API/OpenAPI tests, 104 worker tests, 11 domain tests and 4,012 dashboard tests passed;
14 distinct Chromium scenarios and two actual API/browser workflows passed.
The request return cursor issue found by the browser test was corrected.

Continue the module review. The older approval readers still need post-acquisition
MFA/delegation expiry reproduction and correction before their access contract can
be accepted. Full initial requirement rows remain unverified until their source
and behavior review is completed; these two gap closures do not close the audit.
