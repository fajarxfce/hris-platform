import type { PayrollPaymentTransition } from "../../domain/entities/payroll-payment-transition";
export const payrollPaymentTransitionIntents = ["release", "cancel"] as const;
export type PayrollPaymentTransitionIntent = (typeof payrollPaymentTransitionIntents)[number];
export const payrollPaymentTransitionForIntent: Readonly<
  Record<PayrollPaymentTransitionIntent, PayrollPaymentTransition>
> = { release: "RELEASE", cancel: "CANCEL" };
