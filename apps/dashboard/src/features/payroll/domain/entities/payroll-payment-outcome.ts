import type { PayrollPaymentResult } from "./payroll-payment-reconciliation";
export type PayrollPaymentOutcome = PayrollPaymentResult &
  Readonly<{ confirmedNoTransfer: boolean }>;
