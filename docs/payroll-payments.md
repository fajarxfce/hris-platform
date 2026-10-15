# Payroll payments

Finance prepares payment batches from published payroll assessments. The backend
copies retained employee labels, tax month and planned payment date, and the
current positive balance: entitlement minus confirmed transfers plus confirmed
returns. A request observes that amount; it cannot replace it. A zero or negative
balance has no payable transfer. Original instructions remain immutable.

All finance endpoints require `payroll.pay` and current recent authentication/MFA. They revalidate original/live permissions, credentials and session assurance under payment, people, company, membership, and account guards. Reads repeat that check after acquisition; commands retain recent assurance through original receipt recovery and transaction completion. Expiry rolls back payment, item, reference, progress, journal and receipt changes together. Payroll administration, calculation, or finalization does not imply payment access. A preparer may be a payroll beneficiary, because salary inputs and finalization are independently reviewed; a different account must release the batch.

## Commands

Use `/api/v1/companies/{companyId}/payroll/payments` and a UUID `Idempotency-Key` for every command. Keep the same resource/item IDs, key, and payload when a response is lost. Changed intent gets a new key. Release, cancellation, and reconciliation also require `expectedVersion` and `reason`.

| Method and path | Behavior |
| --- | --- |
| `PUT /{batchId}` | Prepare 1–100 distinct assessment instructions with `title`, `items`, and `reason`. Each item has `id`, `assessmentId`, observed `assessmentRevision`, `paymentVersion`, `amount`, and `destination` containing `bankCode`, `accountNumber`, and `accountName`. |
| `POST /{batchId}/release` | Independently approve immutable instructions; the batch becomes `RELEASED` and its items `PENDING`. |
| `POST /{batchId}/cancel` | Cancel a `PREPARED` batch before release. Instructions and history remain retained. |
| `POST /{batchId}/reconcile` | Record 1–100 distinct pending item outcomes; unresolved items remain pending. The batch closes once all items have a confirmed outcome. |

Each reconciliation result contains `itemId`, `status`, `occurredAt`, `reason`, optional `transactionReference`, and `confirmedNoTransfer`. `SUCCEEDED` requires a bank transaction reference and `confirmedNoTransfer: false`. `FAILED` requires a null reference and `confirmedNoTransfer: true`. Confirmation time must be between release and the current server time.

A timeout or unknown bank result stays `PENDING`; it must not become a confirmed failure. Prepared and pending items reserve their assessment. A cancelled instruction or confirmed no-transfer failure permits another attempt. After a published amendment, an already paid assessment can have a new positive balance; only that observed difference can be prepared. Existing results and destinations cannot be rewritten. At most twenty attempts are retained per assessment, one hundred batches may remain open, and a company retains at most ten thousand batches.

Confirmed transaction references share a company-scoped registry with expense reimbursements. The registry also includes confirmed payroll recoveries. One reference cannot describe two transfers or both a transfer and a return. Concurrent conflicts roll back the item, batch, evidence, progress version, audit/outbox, and operation receipt together. Migration backfills existing successful reimbursements without rewriting their history; an account unable to read all companies cannot silently perform a partial backfill.

New clients send all three observation fields together. Omitting all three remains
compatible only with an original revision-zero assessment without confirmed
payments or recoveries. Partial observations are invalid. A changed revision,
payment version or amount requires explicit review; retrying a previously committed
operation still returns its original receipt before checking current balances.

## Confirmed recoveries

`GET /api/v1/companies/{companyId}/payroll/payslips/{assessmentId}/recoveries/review`
combines the current employee/month reference, independently versioned payment
balance, retained returns and earliest valid confirmation time under one shared
payment guard. It requires finance authority independently of payslip read grants.
The response includes `canConfirm` and every applicable blocker: `NOT_OVERPAID`,
`PENDING_PAYMENT`, `RECOVERY_LIMIT` or `BENEFICIARY`. A beneficiary with finance
read authority can inspect the evidence but cannot confirm their own return.
The review creates no reservation or command. Confirmation still rechecks its
observed revision, payment version, current balance and current beneficiary.

Use `GET /api/v1/companies/{companyId}/payroll/payslips/{assessmentId}/recoveries`
for at most twenty retained return records. `PUT .../recoveries/{id}` records one
confirmed incoming bank amount with an `Idempotency-Key`, `assessmentRevision`,
`paymentVersion`, positive decimal `amount`, `transactionReference`, `occurredAt`
and `reason`. The confirmation cannot exceed the observed recoverable balance,
precede the current publication/latest successful payment, or coexist with an
unresolved instruction. A beneficiary cannot confirm their own return.

Returns are immutable evidence; they never create a negative transfer or an
automatic salary deduction. The return, unique bank reference, payment-progress
version, sync invalidation, journal and receipt commit together. Competing
observations require a fresh balance review; an exact retry recovers its original
result. All finance authorization and assurance checks apply to these endpoints.

## Reads and export

`GET /payables` lists unreserved positive assessments below the attempt limit. `GET /` lists batches, optionally filtered by status. Both accept `from`, `until`, `after`, and `limit`. Dates describe company-local publication/creation days, with a maximum 366-day range and default/maximum pages of 50/200. Follow the returned cursor; it is not a synchronization position.

`GET /{batchId}`, `/history`, and `/results` expose authorized finance detail and immutable evidence. `GET /{batchId}/export` returns a deterministic CSV worksheet only while the batch is released and every item is pending. It retains stable item references, escapes spreadsheet formulas and CSV content, and preserves leading account-number zeros. Repeated downloads do not create a payment or settlement record. Bank-specific formats and direct bank submission are separate integrations.

`GET /{id}/review` combines the current batch, ascending history, reconciliation results and `availableActions` under one shared payment guard. Optional `historyAfter` and `historyLimit` (default 50, maximum 200) page only the history; the returned batch and results belong to the same guarded review. A cursor ahead of the current batch version is rejected after resource authorization. `RELEASE` requires a different account from the preparer and retained assessment evidence; `CANCEL` applies to prepared batches; `RECONCILE` requires pending items and retained evidence; `EXPORT` applies only before any released item is resolved. Hints do not reserve eligibility or replace each command's current permission, evidence and version checks.

## Dashboard

The [dashboard finance workflow](dashboard.md#payroll-payments) covers payable
selection, bank review, independent release, cancellation, CSV download and
reconciliation. Its selection and review retain the observed assessment revision,
payment version and current positive amount through delivery and retry. Payslip
payment summaries show independently versioned balances and retained transfer
amounts. Finance can open balance and return history from either a payment item
or an authorized payslip. The return form requires reviewed versions, a positive
decimal amount, bank reference, company-local confirmation time and a reason.
Definite balance conflicts require explicit review; an uncertain response retains
the exact original return. Partial and complete returns leave the original bank
instructions and results intact. These screens require `payroll.pay` independently
of permission to read a full payslip.
Preparation and result panels retain unsaved input while the
current workspace remains mounted. Lost responses require an explicit retry of
the original command; they do not authorize another bank transfer.

## Employee progress and synchronization

`GET /api/v1/companies/{companyId}/payroll/payslips/{assessmentId}/payments` exposes a separate progress object with `assessmentId`, `version`, and up to twenty attempts. An attempt contains batch/item IDs, status, creation/release/resolution timestamps, its amount and retained assessment revision. It omits bank destinations, transaction references, reasons, and other employees. Access follows `payroll.self.read` ownership or explicit company-wide `payroll.read`.

Progress also carries a self-contained `settlement`: `assessmentRevision`,
`entitlement`, `confirmedPaid`, `confirmedRecovered`, nonnegative `payable` and
`recoverable`, and `currency`. Clients must not combine payment facts with an
independently acquired payslip revision to derive a different balance.

An original unpaid assessment has progress version `0`. Preparation, every item
transition, revised assessment publication and confirmed recovery advance this
separate version. The payslip's version is its assessment revision; a bank event
does not change that revision or historical calculation. The `PAYROLL_PAYMENTS` sync collection carries owned assessment IDs and progress versions in the same transaction as the payment change. See [mobile synchronization](synchronization.md) and [client error/retry policy](mobile-api.md).

Stable domain failures include `payroll_assessment_not_payable`, `payroll_assessment_payment_reserved`, `payroll_payment_attempt_limit`, `payroll_payment_capacity`, `payroll_payment_not_prepared`, `payroll_payment_not_released`, `payroll_payment_item_not_pending`, `payroll_payment_instructions_unavailable`, `payroll_payment_observation_required`, `payroll_payment_balance_changed`, `payroll_payment_recovery_exceeds_balance`, `payroll_amendment_payment_pending`, and `stale_version`. Clients translate codes and stop or resolve conflicts explicitly; they never silently replace an observed version or repeat an unknown bank transfer.
