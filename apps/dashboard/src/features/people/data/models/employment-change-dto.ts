import { z } from "zod";
import type { AssignedEmploymentTermsDto } from "./employment-details-dto";

export type EmploymentChangeDto = {
  version: number;
  terms: AssignedEmploymentTermsDto;
  reason: string;
};
export const employmentChangeReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export type EmploymentChangeReceiptDto = z.infer<typeof employmentChangeReceiptDto>;
