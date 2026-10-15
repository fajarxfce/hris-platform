import type { PayrollPaymentRecoveryInput } from "./payroll-payment-recovery-input";

export type PayrollPaymentRecoveryCommand = PayrollPaymentRecoveryInput &
  Readonly<{
    id: string;
    assessmentId: string;
    assessmentRevision: number;
    paymentVersion: number;
  }>;
