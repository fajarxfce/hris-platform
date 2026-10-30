# Approval read completion assurance — 30 October 2026

The full feature audit found a concrete access gap in seven older approval readers
at Platform `bbb99b48`. They checked MFA after access guards but returned the result
of a later acquisition without checking whether that proof had expired. Request
review also evaluated delegation against the time captured before member lookup;
inbox selection used the time passed into its SQL query.

Ten PostgreSQL-backed reproductions failed at that baseline: eight current/history
metadata read variants returned acquired data after MFA expiry, and request/inbox
reads returned a request after its delegation expired. These are clock-controlled
expiry tests after successful real repository acquisition, not simulated HTTP
errors or new provider configuration requirements.

The corrected readers retain their guarded access projection and validate session
assurance after successful acquisition. Initial/original permission checks and
company/resource guards remain. Request review evaluates delegation at completion.
Inbox review checks the returned current stage and current delegation at completion;
a changed admission returns `approval_changed` rather than silently truncating or
advancing a partially authorized page. Admin repair access remains limited to
blocked inbox items; retained request/history scope continues to include eligible
prior-stage assignees independently.

Only pages containing a delegated assignment need the bounded delegation/member
reads. The existing repository caps delegation acquisition at 201 (200 plus overflow);
member lookup uses those finite delegator IDs. Direct/administrative repair rows do
not create per-row network/database calls. No retry, write, audit, schema or command
receipt is introduced. Cancellation and acquisition failures retain their established
transaction/failure boundaries.

The ten reproductions passed after correction. A compile-time lambda shadowing
mistake in template projection was corrected before that passing run. All 60 approval API cases and 13 domain cases passed, alongside architecture,
formatting and both application packages (157 seconds). Both actual API/browser
approval workflows passed on the final package (42 seconds with fixture startup).
[Measurements](measurements/approval-read-assurance-2026-10-30.json) retain the
baseline failures, final suite counts and practical scope.
The [history increment](approval-history.md) already performs its final assurance
check. Continue the other module and Mobile requirements after this correction.
