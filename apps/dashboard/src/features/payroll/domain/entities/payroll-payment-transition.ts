export const payrollPaymentTransitions = ["RELEASE", "CANCEL"] as const;
export type PayrollPaymentTransition = (typeof payrollPaymentTransitions)[number];
