# Approvals requirement review — 30 October 2026

The five initial Approvals requirement rows and all six detailed contract sections
have now been reviewed against implementation and matching behavior. Local
acceptance is complete after the history addition and read-completion correction.
Full branch CI for the final increment follows its commit. This covers the Platform
approval capability; it does not mark another module or Mobile requirement complete.

| Requirement | Implementation and matching behavior |
| --- | --- |
| `approvals.01`: versioned templates, stages, snapshots, delegation, decisions, inbox | `ApprovalPolicy`, `TemplatePolicy`, shared repositories and immutable V4 records; `ApprovalHttpTest`, `ApprovalAdministrationHttpTest`, `ApprovalCapacityHttpTest`, domain policy tests and real approval administration workflow. |
| `approvals.02`: category/amount selection, assignment, self-approval denial, blocked repair, retained template | Pure selection/snapshot/decision policies; actual snapshot-survives-edit, competing decision, blocked reassignment and monetary-scale tests. Real expense reassignment preserves source claim status and replays the original receipt. |
| `approvals.03`: pure shared transitions and owning feature transactions | `DecideLeaveRequest`, `ReviewExpenseSubmission`, `DecideOvertimeRequest`, `DecidePayrollReview` and amendment review coordinate the shared repository inside their own transactions. Domain has no Spring/SQL/SDK dependencies. Architecture checks and actual leave/expense workflows verify the boundary. |
| `approvals.04`: inbox, request timeline, templates, delegation, blocked items | Dashboard feature routes and independent controllers, including the new history link/page; 14 history/inbox Chromium scenarios, all approval unit/controller tests within the 4,012-case dashboard suite, and real approval administration/leave browser workflows. |
| `approvals.05`: concurrent decisions, expiry/revocation, no self-approval, immutable submitted policy | `ApprovalAccessRaceHttpTest`, `ApprovalReadRaceHttpTest`, `ApprovalAssuranceTest`, `ApprovalHistoryHttpTest`, `ApprovalReadCompletionAssuranceTest`, snapshot and maker policy tests. Ten previously failing completion-expiry cases now pass. |

Detailed review covers the implementation status; access and concurrent changes;
frozen maker exclusions; bounded administration/selection; dashboard; and retained
administration detail/assignee lookup. Exact template rules remain distinct from
current header fields. Bounded selection rejects ambiguous/over-capacity inputs;
archival and owned delegation deactivation remain available for repair. Lookup
projects only active scoped display references. Current member/account/permission
checks and immutable maker exclusions apply independently of configuration names.

The review found and corrected two defects: missing retained decision/reassignment
history, and missing final assurance/delegation expiry checks on older reads.
[History evidence](measurements/approval-history-2026-10-30.json) includes 118
API/OpenAPI tests, 104 worker tests, 11 then-current domain cases, 4,012 dashboard
cases, narrow/localized Chromium observations and two actual API/browser workflows.
[Read-completion evidence](measurements/approval-read-assurance-2026-10-30.json)
adds the ten reproductions, 60 final approval API cases and 13 domain cases. Final
interactive reads create no business writes or new retention/recovery state.

The 201-reassignment API exercise traverses the maximum 200-row page and the final
row without skipping or repeating a revision. A changed request explicitly rejects
continuation. The history UI retains one 50-row page and fences background/company
replacement. All API/resource outcomes remain server-authorized; display IDs do
not grant access.

Continue [the full audit](feature-audit-2026-10-30.md), starting with the remaining
Communications requirements and then the other Platform/Mobile contracts.
