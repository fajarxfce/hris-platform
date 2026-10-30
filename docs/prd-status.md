# Current PRD review

Review reopened 30 October 2026 against all eleven initial module contracts and
Mobile features. The confirmed announcement email gap is implemented and locally accepted; the previous
module-level summary does not establish full feature completion. The requirement
inventory and remaining review are active. The earlier distribution audit passed
its local packaging and independent CI checks. Physical iPhone testing remains
waived; deployment/provider observations retain their separate scope.
[Completion](completion.md) preserves history and [handoff](agent-handoff.md)
identifies current work and runtime ownership.

## Reopened feature audit — 30 October

The user requested proof that every feature is finished. The [full feature audit](feature-audit-2026-10-30.md)
is active. Communications email reminders now have optional authoring, atomic
publication capture, bounded SMTP delivery and dashboard observations, with
[local acceptance](measurements/announcement-email-2026-10-30.json). Earlier
module-level summaries below do not replace requirement-by-requirement review.
[Shared approval history](approval-history.md) closes the next confirmed missing
reader/dashboard capability, with [local acceptance](measurements/approval-history-2026-10-30.json).
The [final assurance correction](approval-read-assurance-2026-10-30.md) and
[Approvals requirement review](approvals-feature-review-2026-10-30.md) locally accept
its five initial rows and detailed sections. The [Communications review](communications-feature-review-2026-10-30.md)
also accepts five rows after the read-completion correction. [Identity](identity-feature-review-2026-10-30.md)
accepts five more against unchanged source and downloaded full-CI results.
[Documents](documents-feature-review-2026-10-30.md) now accepts its five rows and
ten original sections after read/command completion fixes: 243 API, 18 domain,
nine data, quality/packages and four actual browser workflows passed. [Expenses](expenses-feature-review-2026-10-30.md) adds six locally accepted rows
and nine sections, with 335 distinct API cases, 20 Expense domain, 115 Reporting
cases, quality/packages and four actual browser workflows. Twenty-six rows are
reviewed; 35 initial rows and the full Mobile review remain active. Email
`8014d733` and approval `fcfdaf26` each passed all ten full-CI jobs. Follow the
Communications/Documents/Expenses final-source CI and continue Leave and remaining scope.


## Release-configuration audit — accepted

The audit fixes selected release API precedence over per-environment project
properties, optional app-link configuration through signing and packaging, and
31 missing SBOM POM verification entries. The original artifact hashes remain
unchanged. Android checks the normal test configuration before compiling the
selected release; Xcode forwards the selected backend/API explicitly. The 15
portable files retain Starter's application IDs, demo/remote choice and three-job
CI. HRIS's remote-only policy, build-version UI fixtures and explicit dev-network
XML attributes remain in Mobile.

Mobile source `3dc81d00` passed all 4 jobs in full
[CI 40909156570](https://github.com/fajarxfce/hris-mobile/actions/runs/40909156570). Downloaded Apple reports
confirm 104 tests in 28 suites, zero failures/errors/skips;
compilation/tests/Xcode took 791s/122s/1295s. The complete Xcode app, native/domain
artifacts and explicit selected distribution API/backend assertions passed.

Starter port `77063862` passed all 3 jobs in full
[CI 40916187442](https://github.com/fajarxfce/compose-fluent-starter/actions/runs/40916187442). Downloaded Apple reports
confirm 84 tests in 24 suites, zero failures/errors/skips;
compilation/tests/Xcode took 724s/81s/744s. The complete Xcode app, native/domain
artifacts and explicit selected distribution API/backend assertions passed.

Actual local signed Android APK/AAB/SBOM exercises passed in 215.572s on Mobile
and 136.647s on Starter. Both verify the selected release API and native manifest
in APK and AAB, actual signatures, retained domain files and checksums. Mobile
passed 1,860 desktop cases; Starter passed 326 desktop cases in 112 suites. Both
passed 17 distribution regressions. Normal dev builds and production SBOMs were
restored afterward (39s Mobile, 23s Starter), with fixture API/host values absent;
Starter's normal backend remains demo. Every disposable signing key was removed.

A workflow-only follow-up at Mobile `d7e445a8` and Starter `4c794e7` retains
`.well-known` files in the manual artifact upload. The pinned action's actual
`@actions/glob` 0.5.0 selection reproduced five files with both domain statements
missing on each signed fixture; the correction selects all seven and every
checksum matches. Both workflows include hidden files only in the packaged
artifact directory. This separate proof does not invoke upload; application/native
code and ordinary CI definitions remain at the full-CI sources above.

No CI, local check or reusable port remains pending for this audit. No distribution
workflow, upload or phone installation was performed; the installed Mobile APK
remains `72341ef6`. The private server and accepted Google binding are preserved.
Real signed Apple IPA, APNs, public domain/DNS/TLS activation, physical GPS and
production operation retain their separate target requirements. Physical iPhone testing was
explicitly waived; simulator and unsigned Xcode proof does not imply device delivery.

See [distribution evidence](measurements/distribution-2026-10-29.json).

## Implementation and acceptance map

The eleven module screen/acceptance lists were reread against their current
contracts and owning workflows. No source TODO/NotImplemented/coming-soon stub was
found in Platform/Mobile application sources. That search is supplementary, not a
proof that all behavior is correct. The references below are existing implementation
and acceptance evidence; the current CI result must be assessed separately.

| PRD area | Implemented scope and retained evidence | Actual workflow example |
| --- | --- | --- |
| [Identity](modules/identity.md) | Accounts, roles/memberships, invitations, MFA, app sessions and explicit identity binding. Firebase email/password and actual Android Google through HRIS/company/Home are accepted. | [Invitations/session replacement](../apps/dashboard/integration-worker/account-invitations.spec.ts), [bindings](../apps/dashboard/integration/oidc-bindings.spec.ts) |
| [People](modules/people.md) | Effective-dated organization/employment, import, account binding, company transfer and scheduled lifecycle changes. | [Import](../apps/dashboard/integration/employee-imports.spec.ts), [transfer](../apps/dashboard/integration/employment-transfer.spec.ts), [scheduled onboarding](onboarding-schedules.md) |
| [Workforce](modules/workforce.md) | Shifts, holidays, weekly/roster/bulk calendars, immutable attendance/review/corrections, planned/actual overtime and closing/adjustments. Physical GPS remains external. | [Attendance](../apps/dashboard/integration/attendance.spec.ts), [overtime](../apps/dashboard/integration/overtime.spec.ts), [closings](../apps/dashboard/integration-worker/work-periods.spec.ts) |
| [Leave](modules/leave.md) | Effective policies, reviewed creation, independent staged actions, balances, accrual/year close/carry/expiry and team calendar. | [Creation/replay](../apps/dashboard/integration/leave-creation.spec.ts), [expiry](../apps/dashboard/integration-worker/leave-carry-expiry.spec.ts), [calendar](../apps/dashboard/integration/leave-calendar.spec.ts) |
| [Approvals](modules/approvals.md) | Versioned templates, immutable assignee stages, delegation, independent decisions and blocked/reassigned workflows. Business effects stay in their owning modules. | [Delegation](../apps/dashboard/integration/approval-delegations.spec.ts), [leave decisions](../apps/dashboard/integration/leave-actions.spec.ts) |
| [Expenses](modules/expenses.md) | Categories, edited/versioned claims, evidence/duplicate review, correction/return, independent payment release and per-item reconciliation. External bank submission is manual in the initial scope. | [Authoring/review](../apps/dashboard/integration/expense-claims.spec.ts), [payments](../apps/dashboard/integration/expense-payments.spec.ts) |
| [Payroll](modules/payroll.md) | Decimal rules, reviewed policy/compensation/tax inputs, durable calculations, original period comparison, independent finalization, payslips, payments and referenced amendments. | [Calculations](../apps/dashboard/integration-worker/payroll-calculations.spec.ts), [comparison](../apps/dashboard/integration-worker/payroll-comparison.spec.ts), [amendments](payroll-amendments.md) |
| [Documents](modules/documents.md) | Scoped revisions, resumable transfer, bounded inspection, immutable evidence, retention/hold/retirement and private range downloads. | [Transfers](../apps/dashboard/integration/documents.spec.ts), [retention lifecycle](../apps/dashboard/integration/document-retention-lifecycle.spec.ts), [scanner workload](workload.md) |
| [Communications](modules/communications.md) | Audiences, draft/scheduled publication, archive, owned inbox/acknowledgement, device registration and administrative delivery observations. Actual FCM foreground/background delivery and notification navigation passed on the owned Android emulator. | [Publication/worker](../apps/dashboard/integration-worker/communications.spec.ts), [delivery](announcement-delivery.md), [native SDK registration](native-push-acceptance.md), [actual FCM](native-fcm-acceptance.md) |
| [Reporting](modules/reporting.md) | Scoped reports/drill-down/catalogue, owner-bound saved filters, durable multipart export and retirement. The unmodified physical one-hour export grace passed. | [Saved filters](../apps/dashboard/integration/saved-report-filters.spec.ts), [export](../apps/dashboard/integration-worker/report-exports.spec.ts), [elapsed retirement](export-retention-acceptance.md) |
| [Administration](modules/administration.md) | Effective settings/availability, integration observations, jobs, scoped/global audit and private encrypted recovery. | [Worker integration](../apps/dashboard/integration-worker/worker-integrations.spec.ts), [account audit](../apps/dashboard/integration-worker/platform-account-audit.spec.ts), [schema-101 recovery](recovery-set.md) |

Bank-specific submission formats, further tax rule packs and other workload corpora
must be assessed against the explicit initial scope, not inferred as missing work
from historical future-work prose. Do not fabricate business rules or production
configuration to close an acceptance checkbox.

## Native pending-action coverage

Actual Android 35/API acceptance now has a recovery example for each of the six
feature observers. Each row retains its own procedure, server assertions and limits.
These results do not imply every combination was exercised on every target.

| Feature | Accepted native recovery | Stable summary reload | Final server invariant |
| --- | --- | --- | --- |
| Inbox | [Committed read-response loss](native-acceptance.md) | 30 → 30 | One read result at version 1; acknowledgement remains absent |
| Attendance | [Capture response loss/offline cold start](native-attendance-acceptance.md) | 21 → 21 | One event, original timestamp, independent review still pending, zero accepted minutes |
| Overtime | [Plan response loss/offline cold start](native-overtime-acceptance.md) | 39 → 39 | One plan/history entry, version 0, 165 minutes |
| Leave | [Submission response loss/offline cold start](native-leave-acceptance.md) | 33 → 33 after navigation settles | One request/history/approval, 0.5 reserved once, 1.5 available |
| Expenses | [Save response loss/offline cold start](native-expense-acceptance.md) | 30 → 30 before Save; 43 → 43 after loss | One draft/line/history entry, IDR 25,000.25 |
| Documents | [Interrupted upload and failed-inspection review](native-upload-acceptance.md), [partial download](native-download-acceptance.md) | 32 → 32 / 42 → 42 for upload/review | One retained revision; original chunk/manifest and explicit retry/cancellation |

These HRIS fixtures required no generic Starter code change and used Mobile
application `02743a9f`. Later Mobile `678783b3` corrects only English Expense count
copy; local root/Android checks passed. Mobile remains the proving ground for
generic fixes. The later iOS provider at Mobile `43cd8ec6` passed full CI before
the generic Google port to Starter `e0c6071`; these sources have separate proof below.

## Current CI ownership

| Repository | Current verification | Source/result boundary |
| --- | --- | --- |
| Platform | Corrected full [40820912608](https://github.com/fajarxfce/hris-platform/actions/runs/40820912608), head `9345c19b`, passed all ten jobs | First run passed tests/restore but failed hidden-path report upload. Run `40820005213` passed backend/recovery and uploaded its checked summary, but failed a browser locator. The correction passed all four backend/dashboard shards, actual API/worker/export integration and schema-101 restore with a downloaded sanitized report. The owned CI ref was removed after verification. [Measurements](measurements/ci-2026-10-29.json) retain each run and failure. |
| Mobile | Full [CI 40909156570](https://github.com/fajarxfce/hris-mobile/actions/runs/40909156570), source `3dc81d00`, passed all four jobs | 104 Apple cases in 28 downloaded suites, no failures/errors/skips, Xcode with explicit release API and configured associations. Local signed APK/AAB/SBOM passed in 215.572s; the installed phone remains on `72341ef6`. |
| Starter | Full [CI 40916187442](https://github.com/fajarxfce/compose-fluent-starter/actions/runs/40916187442), port `77063862`, passed all three jobs | 84 Apple cases in 24 downloaded suites, no failures/errors/skips, Xcode with explicit release API and configured associations. Local signed APK/AAB/SBOM passed in 136.647s; 326 desktop and 17 distribution cases passed. Source proof preceded the port. |

All final jobs and downloaded Apple reports/log success markers were inspected.
No CI or owned local test remains active from this increment. Platform's newer
public handoff separately passed 4002 unit/14 browser cases; its API/worker code
and schema remain on the previously accepted baseline. Prior failures remain
recorded separately from their successful corrections. No production distribution
workflow was invoked by these CI dispatches.

## External acceptance still required

The supplied server credential resolved the FCM prerequisite. [Actual Android FCM](native-fcm-acceptance.md)
passed two real publications and generic foreground/background notifications,
correct selected account/company navigation and unchanged Inbox read/ack state.
The fixture passed in 6.2m/6.6m including setup, with 55 requests, no native company
mutation and complete token retirement/cleanup. Scope replacement/duplicate/revoked
hints retain separate automated coverage; these results do not claim every device
or scheduling state. [Measurements](measurements/native-fcm-2026-10-29.json) retain
the two initial timeouts and final pass. The sender credential path is in the handoff;
do not repeat the resolved question or commit its contents.

Actual Google sign-in on the installed Android phone is now accepted: the user
confirmed company/Home and a new native session appeared in authorized server
metadata. The explicit binding preserved the existing employee grants and retired
the fictional provider UID. [Google evidence](measurements/native-google-2026-10-29.json)
contains no real email, UID, token or credential.

| Work | Current evidence | Missing prerequisite |
| --- | --- | --- |
| iOS push/auth and HTTPS application links | Apple providers and the preceding Starter ports passed simulator/Xcode CI. Mobile `3dc81d00` and Starter `7706386` passed all full CI jobs with 104/84 native cases and Xcode, including configurable associations, ASCII HTTPS admission and selected distribution API properties. The [public web handoff](mobile-app-links.md) passed local dashboard checks. | Physical iPhone testing is explicitly waived for this pass. Domain selection/publication and APNs credentials remain deployment settings; no actual OS verification or APNs delivery is claimed. |
| Physical location and target deployment | Explicit no-location review, emulator/API and constrained 5,000-employee/scanner workload measurements accepted. Deployment/recovery guidance is implemented. | Available authorized device/environment, real provider configuration and a target-specific execution. A Git push is not live release authorization. |

Earlier CI and generic ports are accepted. The discovered iOS Firebase adapter
gap now has implementation, full Mobile proof and independent Starter proof.
Continue the device/provider rows when their inputs
become available, and reopen other implementation for an observed failure or
documented requirement gap. Mark rows complete only after actual acceptance. The repository should never
claim that a fixture sent a real FCM message, completed Google sign-in, or verified
a physical deployment when it did not.
