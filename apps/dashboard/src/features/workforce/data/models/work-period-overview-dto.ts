import { z } from "zod";
import { workPeriodDto } from "./work-period-dto";

export const workPeriodOverviewDto = z.object({
  month: z.string().regex(/^\d{4}-\d{2}$/u),
  companyDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/u),
  companyTimezone: z.string().min(1).max(100),
  period: workPeriodDto.nullable(),
  attempt: z
    .object({
      id: z.uuid(),
      status: z.enum(["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED"]),
      totalEmployees: z.number().int().min(0).max(5000),
      completedEmployees: z.number().int().min(0).max(5000),
      cancellationRequested: z.boolean(),
      failureCode: z.string().min(1).max(80).nullable(),
      createdAt: z.string().max(40),
      finishedAt: z.string().max(40).nullable(),
    })
    .nullable(),
  availableActions: z.array(z.string().min(1).max(80)).max(16),
});
export type WorkPeriodOverviewDto = z.infer<typeof workPeriodOverviewDto>;
