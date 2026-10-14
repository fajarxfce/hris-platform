import { payrollAmountMinor } from "./payroll-policy-validation";
/** Finalized payment items support up to twelve integral digits and two decimal places. */
export const payrollPaymentAmountMinor = (value: string): bigint | null =>
  payrollAmountMinor(value, 99_999_999_999_999n);
