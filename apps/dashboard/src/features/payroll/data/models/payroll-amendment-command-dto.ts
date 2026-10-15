import { z } from "zod";
import type { payrollAmendmentTermsDto } from "./payroll-amendment-dto";

export type PayrollAmendmentCommandDto = Readonly<{ expectedVersion: number; reason: string }>;
export type PayrollAmendmentSaveDto = z.infer<typeof payrollAmendmentTermsDto> & {
  firstAssessmentId: string;
  expectedVersion: number | null;
  reason: string;
};
export const payrollAmendmentReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(999),
});
export type PayrollAmendmentReceiptDto = z.infer<typeof payrollAmendmentReceiptDto>;
