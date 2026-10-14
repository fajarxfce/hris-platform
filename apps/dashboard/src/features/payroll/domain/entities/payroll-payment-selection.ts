import type { PayrollPayable } from "./payroll-payable";
import type { PayrollPaymentDestination } from "./payroll-payment-destination";
export type PayrollPaymentSelection = Readonly<{
  payable: PayrollPayable;
  destination: PayrollPaymentDestination | null;
}>;
