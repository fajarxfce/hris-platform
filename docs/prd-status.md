# Current PRD review

Reviewed 29 October 2026. The implementation queue for this development pass is
reconciled for the eleven initial module areas, Mobile coverage and HTTPS links.
The subsequent release-configuration audit below is active after concrete build
failures were reproduced; its reusable port passed local checks and awaits independent hosted CI. The user waived physical
iPhone tests. Actual domain activation, APNs, physical GPS and production operation
remain separate target observations; this is not a production-release declaration.
[Completion](completion.md) preserves detailed history; [handoff](agent-handoff.md)
contains current sources, device delivery and runtime preservation instructions.

## Release-configuration audit — Starter CI active

Mobile source `3dc81d00` passed all four jobs in full
[CI 40909156570](https://github.com/fajarxfce/hris-mobile/actions/runs/40909156570).
Downloaded Apple reports confirm 104 cases in 28 suites with zero failures/errors/
skips; compilation/tests/Xcode took 791s/122s/1295s. Xcode's selected release API
and remote backend were explicitly checked, along with native/domain artifacts.
Its actual signed APK/AAB/SBOM fixture passed in 215.572s; all 1,860 desktop cases
and 17 distribution regressions passed. Thirty-one missing POM hashes match the
canonical HTTPS artifacts and preserve all prior hashes.

The audit fixes reproduced release API precedence, optional app-link input and
signer/domain artifact matching, release SBOM verification, HRIS demo selection,
build-version fixtures and explicit dev XML lint attributes. No API/schema or
business policy changed. The 15 portable files retain Starter's demo choice and
IDs; HRIS policy, UI fixtures and private dev network XML remain downstream.

Full [Starter CI 40916187442](https://github.com/fajarxfce/compose-fluent-starter/actions/runs/40916187442) is active at port `7706386`. All local
checks passed: 326 desktop cases, 17 distribution and ten runner cases, actual
signed APK/AAB/SBOM in 136.647s, and restored normal demo/dev plus production SBOM
in 23s. Both packages retain the selected API/registration; actual signatures
and packaged domain files/checksums passed. No upload or installation occurred.
Final hosted jobs and downloaded Apple reports/Xcode markers remain required.

Preserve the installed phone, Google binding and private runtime. No distribution
workflow was dispatched. Physical Apple signing, domain publication and production
release remain distinct target operations. See the tracked
[distribution evidence](measurements/distribution-2026-10-29.json) and the
[Mobile handoff](https://github.com/fajarxfce/hris-mobile/blob/feat/native-push/docs/agent-handoff.md).

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
| Mobile | Full [40895095518](https://github.com/fajarxfce/hris-mobile/actions/runs/40895095518), source `72341ef6`, passed all four jobs | 104 native cases in 28 downloaded suites, no failures/errors/skips, complete Xcode and configured associations. The source foundation `d36a1bc9` separately passed real Android scope/offline/archive acceptance. Final normal APK is installed on the phone. |
| Starter | Full [40895798634](https://github.com/fajarxfce/compose-fluent-starter/actions/runs/40895798634), port `738db36`, passed all three jobs | 84 native cases in 24 downloaded suites, no failures/errors/skips, Xcode and configured associations. Local root/Android passed 326 desktop, 11 Chromium and six build-logic cases. Source Mobile proof preceded the respective port increments. |

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
| iOS push/auth and HTTPS application links | Apple providers and the preceding Starter ports passed simulator/Xcode CI. Final Mobile `72341ef6` and Starter `738db36` passed all full CI jobs with 104/84 native cases and Xcode, including configurable native associations/ASCII HTTPS admission. The [public web handoff](mobile-app-links.md) passed local dashboard checks. | Physical iPhone testing is explicitly waived for this pass. Domain selection/publication and APNs credentials remain deployment settings; no actual OS verification or APNs delivery is claimed. |
| Physical location and target deployment | Explicit no-location review, emulator/API and constrained 5,000-employee/scanner workload measurements accepted. Deployment/recovery guidance is implemented. | Available authorized device/environment, real provider configuration and a target-specific execution. A Git push is not live release authorization. |

Earlier CI and generic ports are accepted. The discovered iOS Firebase adapter
gap now has implementation, full Mobile proof and independent Starter proof.
Continue the device/provider rows when their inputs
become available, and reopen other implementation for an observed failure or
documented requirement gap. Mark rows complete only after actual acceptance. The repository should never
claim that a fixture sent a real FCM message, completed Google sign-in, or verified
a physical deployment when it did not.
