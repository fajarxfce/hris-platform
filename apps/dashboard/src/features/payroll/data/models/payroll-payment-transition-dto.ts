import { z } from "zod";
import { payrollPaymentVersionDto } from "./payroll-payment-dto";
export type PayrollPaymentTransitionDto = Readonly<{ expectedVersion: number; reason: string }>;
export const payrollPaymentTransitionReceiptDto = z.object({
  id: z.uuid(),
  version: payrollPaymentVersionDto,
});
export type PayrollPaymentTransitionReceiptDto = z.infer<typeof payrollPaymentTransitionReceiptDto>;
