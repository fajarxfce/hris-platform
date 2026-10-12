import { z } from "zod";
import { scheduledDayDto, workforceEmployeeDto } from "./calendar-dto";

export const overtimePlanningReviewDto = z.object({
  employee: workforceEmployeeDto,
  workDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/u),
  companyDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/u),
  timezone: z.string().min(1).max(128),
  schedule: scheduledDayDto,
  revision: z.string().regex(/^[0-9a-f]{64}$/u),
});
export type OvertimePlanningReviewDto = z.infer<typeof overtimePlanningReviewDto>;
export type OvertimePlanningSearchDto = Readonly<{
  workDate: string;
  query: string;
  after: string | null;
}>;
