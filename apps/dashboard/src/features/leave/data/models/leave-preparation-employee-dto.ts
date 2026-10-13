import { z } from "zod";
import { leaveDateDto, leaveVersionDto } from "./leave-value-dto";

export const leavePreparationDirectoryDto = z.object({
  companyDate: leaveDateDto,
  timezone: z.string().min(1).max(100),
  employees: z.object({
    items: z
      .array(
        z.object({
          id: z.uuid(),
          employeeNumber: z.string().min(1).max(32),
          legalName: z.string().min(1).max(200),
          status: z.enum(["PROBATION", "ACTIVE", "SUSPENDED", "ENDED"]),
          version: leaveVersionDto,
        }),
      )
      .max(20),
    nextCursor: z.string().min(1).max(32).nullable(),
  }),
});
export type LeavePreparationDirectoryDto = z.infer<typeof leavePreparationDirectoryDto>;
