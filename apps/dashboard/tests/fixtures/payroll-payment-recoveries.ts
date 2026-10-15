import type { PayrollPaymentRecoveryReviewDto } from "../../src/features/payroll/data/models/payroll-payment-recovery-dto";
import { payrollIdentifier } from "./payroll-payment-identifiers";

export function payrollRecoveryReview(company = 0): PayrollPaymentRecoveryReviewDto {
  const assessment = payrollIdentifier(110100, company);
  return {
    assessmentId: assessment,
    employmentId: payrollIdentifier(120000, company),
    employeeNumber: "EMP-001",
    employeeName: "Finance Employee 1",
    taxMonth: "2026-09",
    publishedAt: "2026-10-14T06:00:00.123456Z",
    payments: {
      assessmentId: assessment,
      version: 5,
      settlement: {
        assessmentRevision: 1,
        entitlement: "149000.50",
        confirmedPaid: "150000.75",
        confirmedRecovered: "100.00",
        payable: "0.00",
        recoverable: "900.25",
        currency: "IDR",
      },
      attempts: [
        {
          batchId: payrollIdentifier(80001, company),
          itemId: payrollIdentifier(90100, company),
          amount: "150000.75",
          assessmentRevision: 0,
          status: "SUCCEEDED",
          createdAt: "2026-10-13T05:00:00Z",
          releasedAt: "2026-10-13T06:00:00Z",
          resolvedAt: "2026-10-13T06:01:00Z",
        },
      ],
    },
    recoveries: [
      {
        id: payrollIdentifier(130001, company),
        assessmentId: assessment,
        assessmentRevision: 1,
        paymentVersion: 4,
        amount: "100.00",
        currency: "IDR",
        transactionReference: "RETURN-ORIGINAL",
        occurredAt: "2026-10-14T06:10:00Z",
        recordedAt: "2026-10-14T06:15:00Z",
        actorId: payrollIdentifier(502, company),
        reason: "Confirmed incoming bank transfer",
      },
    ],
    earliestConfirmationAt: "2026-10-14T06:00:00.123456Z",
    canConfirm: true,
    blockers: [],
  };
}
export const payrollRecoveryInput = {
  amount: "500.25",
  transactionReference: "RETURN-NEW",
  occurredAt: "2026-10-14T06:30:00Z",
  reason: "Confirmed incoming funds",
};
