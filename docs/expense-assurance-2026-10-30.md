# Expense assurance and admitted inputs — 30 October 2026

The full requirement review at `8e5dd3db` reproduced twenty-three failures across
twenty-six cases. Fifteen payment reads/commands/replays accepted an admitted MFA
proof without current factor enrollment. Two receipt reads returned evidence after
delegation expiry; one began object I/O after expiry during preparation. Five input
cases changed the admitted draft amount, receipt list, category contracts, payment
candidate selection or reconciliation audit count while the command waited.
Three ordinary-scope receipt controls already passed.

Eleven finance use cases now retain their guarded identity projection and use a
named pure policy for current enrollment, original/live grants, session assurance
and recent authentication. These checks remain through acquisition, original
receipt replay and journal completion, with no additional finance query. Existing
failure classifications and atomic rollback remain.

Receipt metadata/preparation/completion resolve any necessary delegation before
acquiring actor/delegator account guards in identifier order. A fresh guarded
identity acquisition follows those locks. Grants, ordinary scope and delegation
expiry are checked before content acquisition and again before releasing metadata
or bytes. An ordinary claim reader retains their independent scope after delegation
expiry. Storage remains outside SQL transactions, and owned acquired buffers are
cleared on success or failure. This adds one identity acquisition per short receipt
transaction; delegate locks and grants are needed only for delegated admission.

Draft lines and nested receipt identifiers, policy contract selections, payment
instructions and reconciliation outcomes are detached before command waits.
Copies retain only the existing maximum plus one overflow sentinel before
validation, so input copying remains bounded and oversized requests are rejected.
Operation fingerprints, stored facts and journal counts use that admitted input.
The original command remains explicitly recoverable after a lost response.

The twenty-three-case baseline ran in 77 seconds (twenty failures); three further
input cases failed in 51 seconds. The first test compilation used `number` instead
of the repository parameter name `ordinal`; that fixture warning was corrected
before genuine reproduction. The missing retained receipt initially failed by
accessing an absent array item; the final assertion explicitly checks its count.
All thirty-two targeted tests passed in 105 seconds, including six existing policy
assurance cases. Broad acceptance passed 329 API, 20 Expense domain and 115 Reporting domain/data/
delivery cases, plus formatting, architecture and both packages (590 seconds).
It includes three delegator-account wait regressions and final bounded copies.

Subsequent review reproduced six further decision failures: approve/reject/return
could commit after delegation expired during duplicate-evidence acquisition or
journal completion (47 seconds). The fixture needed its review-helper superclass
before it compiled; the six failures then reproduced against unchanged decision
source. `ReviewExpenseSubmission` now evaluates current-stage policy at the actual
decision time and again after journaling, in addition to MFA. It reuses the complete
pure decision policy, so assignment to another stage cannot authorize this one.
The final decision/API run passed all 62 cases plus 20 domain cases, formatting,
architecture and both packages (145 seconds). Together with the earlier broad
run, 335 distinct API cases pass. The four final API/browser workflows passed in 1.2 minutes including setup:
claim authoring/return/review, finance preparation/release/reconciliation, effective
policy authoring and immutable expense reporting. The [measurement](measurements/expense-assurance-2026-10-30.json)
retains exact selections, source boundaries and both package hashes. Full-source
CI remains separately tracked.

No schema, constructor, DI dependency, transport contract or client implementation
changes. The private runtime, Google binding, phone, mail and bank processing remain
untouched. The full Platform/Mobile requirement audit remains active.
