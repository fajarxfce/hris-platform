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
The current application publishes durable Inbox records and dispatches device FCM
hints. Its domain dependencies and DI have no MailRepository integration; the
existing SMTP sender/queue serves only identity invitations and recovery. No
announcement email delivery, channel selection or email delivery observation is
implemented. This is an implementation gap, not missing live SMTP credentials.

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
