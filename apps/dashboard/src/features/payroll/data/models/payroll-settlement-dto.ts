import { z } from "zod";
import { payrollPaymentTotalDto } from "./payroll-payment-dto";

export const payrollSettlementDto = z.object({
  assessmentRevision: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  entitlement: payrollPaymentTotalDto,
  confirmedPaid: payrollPaymentTotalDto,
  confirmedRecovered: payrollPaymentTotalDto,
  payable: payrollPaymentTotalDto,
  recoverable: payrollPaymentTotalDto,
  currency: z.literal("IDR"),
});
export type PayrollSettlementDto = z.infer<typeof payrollSettlementDto>;
