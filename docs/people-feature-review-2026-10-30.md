# People requirement review — 30 October 2026

Four of the five initial People rows are accepted against their implementation and
matching completed tests. The identity-document part of `people.03` is implemented
and passes backend/dashboard/local Mobile/native recovery; complete replacement
Mobile CI remains outstanding. Keep that row unverified until the remaining result
is checked. This review does not establish completion of the entire product.

## Initial requirements

| Row | Source and observed behavior | Result |
| --- | --- | --- |
| `people.01` | Organization owns companies and typed branch/department/position/cost-center units; People owns person/employment/reporting history. `PeopleHttpTest` proves append-only effective history, archived-reference replay, foreign-company rejection, concurrent employee-number uniqueness without orphan people, and reporting cycles. `OrganizationReadHttpTest` and the final Organization completion regressions preserve scoped structure and authority. | Accepted |
| `people.02` | Global person/account identity remains separate from company employee number and employment terms. Payroll owns effective compensation separately. `EmploymentRevisionHttpTest`, `EmploymentCancellationHttpTest`, lifecycle and schedule suites cover probation, contract changes, future cancellation and explicit onboarding/offboarding. `EmploymentTransferHttpTest` and the actual browser transfer preserve person/history, create a distinct destination employment, retain other valid source memberships, serialize competing transfers and roll back both companies on failure. | Accepted |
| `people.03` | Person nationality, Documents identity/category/printed validity and Payroll verified tax residency have independent models and authority. Import preview/apply/error/duplicate/row-outcome behavior passes actual API/worker tests; historical references are retained through inactive units and ended employment. The new metadata path passes schema-103 restore and actual Android/API recovery after a later admin edit. Complete replacement Mobile CI is still required for this newly implemented subrequirement. | Unverified pending CI |
| `people.04` | Routed company/employee directories, organization catalog/editor, employment/history/profile/transfer tabs, CSV import/preview/results, lifecycle tasks/templates and scheduled changes exist. Their controlled-response browser suites cover original command recovery, scope replacement, hidden/disposed reads and navigation. Five actual API/browser/worker workflows pass for import, transfer, account binding and scheduled onboarding/offboarding. | Accepted |
| `people.05` | Concurrent uniqueness, same-company references, immutable effective revisions, import lease/replay/rollback and original history are covered by the People/import/transfer suites. `PayrollRunCutoffHttpTest`, `PayrollFinalizationSecurityHttpTest` and immutable finalization evidence prevent changed employment from replacing approved historical payroll. A transfer cannot merge distinct employment identities into a second assessment of the same person. | Accepted |

## Detailed contract coverage

The fourteen original sections retain their distinct boundaries:

| Section | Implementation and matching verification |
| --- | --- |
| Owned profile synchronization | `GetSelfProfileRecord` and the People publication worker retain owner-only private snapshots and an independent feed revision. `SelfProfileRecordHttpTest` covers coherent publication, old cursors, current scope, cancellation and removal of module availability. Ordinary age does not rewrite retained facts. |
| Implementation status | Create/revise/organization policies and constrained immutable records implement the initial ownership/history contract. The broad 497-case API selection includes People and cross-feature consumers; final Organization DI is validated in API and worker contexts. |
| Personal profiles | `GetPersonProfile`, `SavePersonProfile` and history use independent profile versions and owning-company authority. `PersonProfileHttpTest` covers concurrent edits, explicit private scope, original replay and audit rollback. Public directories omit private fields. |
| Scheduled employment changes | `CancelEmploymentRevision` retains immutable cancellation records and company-date eligibility. Named revision reads retain the original revision and current version; cancellation-date and assurance regressions preserve old receipts after the date changes. |
| Cross-company transfers | `TransferEmployee` and `GetEmploymentTransferReview` acquire ordered scopes/guards and preserve the source person/history. Ten transfer API cases and the actual transfer browser workflow cover atomicity, concurrency, account access, lifecycle/reporting dependencies and immediate dates in both companies. |
| Lifecycle checklists | Separate template, case, task, assignment and completion use cases freeze checklist definitions. Lifecycle/API access/snapshot suites cover required tasks, self/last-admin protection, scoped assignees and late writes. Onboarding completion does not grant access; offboarding ends employment and conditionally revokes only its company membership. |
| Employee imports | Preview and apply use bounded typed proposals, explicit partial confirmation and separately fenced worker steps. Eight core API cases include duplicate competing imports, preview conflicts, audit rollback, lease rejection, cancellation/resume and live revocation. The real browser retains original start/cancel commands after response loss. |
| Account binding | `BindPersonAccount` requires an independent authorized operator and owning company; it neither infers identity from email nor grants membership. API enrollment/assurance cases and the actual browser/native lookup verify one original binding receipt and owner-only profile access. |
| Dashboard employee creation | `employee-creation.spec.ts` covers distinct person/employment IDs, dated references, confirmation and protected retained commands. The backend independently validates all proposed references. |
| Lifecycle template dashboard | `lifecycle.spec.ts` covers bounded template catalog/detail/editing and immutable commands. Template changes leave existing cases frozen. |
| Lifecycle case and task dashboard | Case, task, assignment, initiation, cancellation/onboarding completion and offboarding browser suites keep independent read/command authority, expected versions and cancellation ownership. Scheduled onboarding/offboarding additionally pass actual worker/browser workflows. |
| Mobile employment discovery | `ListSelfEmployments` and `SelfEmploymentHttpTest` enforce owner-only effective records and bounded pages even for broader readers. Missing linkage yields an empty page; private data requires its separate route. |
| Company employee directory | `ListCompanyDirectory`/department discovery and eleven `CompanyDirectoryHttpTest` cases cover minimal current coworkers, literal search, filtering before pagination and current original/live grants after waits. |
| Public coworker synchronization | `GetDirectoryRecord` plus bounded People projection workers publish public facts and tombstones. Eight canonical/feed API cases cover non-owner company readers, foreign records, grant/credential changes and independent bootstrap/change admission. |

Scheduled onboarding/offboarding and final read/command assurance added after the
original section inventory also retain their separate accepted evidence. The
read correction fixed 28 genuine failures; command/Organization work fixed 50,
with four already-correct controls kept distinct. Lifecycle collection inputs are
copied before waits. Availability remains admission policy; it does not interrupt
an already-admitted business transaction or replace worker execution authority.

## Evidence and limits

People and Organization implementation trees and dashboard feature trees are
unchanged from accepted `158b81ac`. Its [full CI](https://github.com/fajarxfce/hris-platform/actions/runs/41032184925)
passed all ten jobs. The later metadata/recovery source `292a30f1` also passed all
ten [jobs](https://github.com/fajarxfce/hris-platform/actions/runs/41043798660), covering
the complete current application graphs. This documentation review adds no new
production code and does not rerun those accepted suites.

[Read evidence](measurements/people-read-assurance-2026-10-30.json) and
[command evidence](measurements/people-command-assurance-2026-10-30.json) preserve
the failed baselines, corrected fixtures and successful selections: 497 broad API,
52 domain, fourteen data and 104 ordinary worker cases, with the separately
accepted one-hour worker case explicitly skipped in the ordinary run. Three actual
browser workflows passed in 36.1s and two worker/browser workflows in 36.8s including
setup. The failed combined worker fixture invocation is not relabelled a pass.

[Native metadata acceptance](native-metadata-acceptance.md) preserves the actual
two offline restarts, original replay, later header/history and binary identity.
It does not establish antivirus scanning; that remains covered separately by the
existing Document acceptance. Mobile CI `41071828262` passed 104 Apple cases and
the complete Xcode simulator app, but Linux failed a browser test deadline.
The corrected 75-case local browser run passes; replacement `41077194465` remains
pending. Physical iPhone testing is waived. Deployment state and Google/FCM
acceptance are separate from this requirement review.
