import { z } from "zod";
import { leaveVersionDto } from "./leave-value-dto";

export const leavePolicyDto = z.object({
  typeId: z.uuid(),
  code: z.string().min(1).max(32),
  revision: leaveVersionDto,
  name: z.string().min(1).max(200),
  paid: z.boolean(),
  allowPartialDays: z.boolean(),
  minServiceMonths: z.number().int().min(0).max(120),
  allowedContracts: z
    .array(z.enum(["PERMANENT", "FIXED_TERM"]))
    .min(1)
    .max(2),
  maxRequestDays: z.number().int().min(1).max(366),
  attachmentRequired: z.boolean(),
});
