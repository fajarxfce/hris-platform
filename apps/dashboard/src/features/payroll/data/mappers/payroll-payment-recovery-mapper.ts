import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { PayrollPaymentRecoveryReview } from "../../domain/entities/payroll-payment-recovery-review";
import { payrollPaymentAmountMinor } from "../../domain/policies/payroll-payment-amount";
import { payrollSettlementAmountMinor } from "../../domain/policies/payroll-settlement-amount";
import type { PayrollPaymentRecoveryReviewDto } from "../models/payroll-payment-recovery-dto";
import { toPayrollPaymentProgress } from "./payroll-payslip-mapper";

export function toPayrollPaymentRecoveryReview(
  raw: PayrollPaymentRecoveryReviewDto,
  company: CompanyId,
  assessment: string,
): PayrollPaymentRecoveryReview {
  const payments = toPayrollPaymentProgress(raw.payments, company, assessment);
  const published = utcInstantMicroseconds(raw.publishedAt),
    earliest = utcInstantMicroseconds(raw.earliestConfirmationAt);
  if (
    raw.assessmentId.toLowerCase() !== assessment ||
    published === null ||
    earliest === null ||
    raw.canConfirm !== (raw.blockers.length === 0) ||
    new Set(raw.blockers).size !== raw.blockers.length ||
    new Set(raw.recoveries.map((record) => record.id.toLowerCase())).size !==
      raw.recoveries.length ||
    new Set(raw.recoveries.map((record) => record.transactionReference)).size !==
      raw.recoveries.length
  )
    throw new InvalidHttpResponseError();
  let expectedEarliest = published;
  for (const payment of payments.attempts) {
    if (payment.status !== "SUCCEEDED") continue;
    const at = payment.resolvedAt === null ? null : utcInstantMicroseconds(payment.resolvedAt);
    if (at === null) throw new InvalidHttpResponseError();
    if (at > expectedEarliest) expectedEarliest = at;
  }
  if (earliest !== expectedEarliest) throw new InvalidHttpResponseError();
  let recovered = 0n;
  const records = raw.recoveries.map((record) => {
    const amount = payrollPaymentAmountMinor(record.amount),
      occurred = utcInstantMicroseconds(record.occurredAt),
      recorded = utcInstantMicroseconds(record.recordedAt);
    if (
      record.assessmentId.toLowerCase() !== assessment ||
      record.assessmentRevision > payments.settlement.assessmentRevision ||
      record.paymentVersion >= payments.version ||
      amount === null ||
      amount <= 0n ||
      occurred === null ||
      recorded === null ||
      occurred > recorded
    )
      throw new InvalidHttpResponseError();
    recovered += amount;
    return Object.freeze({
      ...record,
      id: record.id.toLowerCase(),
      assessmentId: assessment,
      actorId: record.actorId.toLowerCase(),
    });
  });
  if (recovered !== payrollSettlementAmountMinor(payments.settlement.confirmedRecovered))
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId: company,
    assessmentId: assessment,
    employmentId: raw.employmentId.toLowerCase(),
    employeeNumber: raw.employeeNumber,
    employeeName: raw.employeeName,
    taxMonth: raw.taxMonth,
    publishedAt: raw.publishedAt,
    payments,
    recoveries: Object.freeze(records),
    earliestConfirmationAt: raw.earliestConfirmationAt,
    canConfirm: raw.canConfirm,
    blockers: Object.freeze([...raw.blockers]),
  });
}
