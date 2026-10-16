import { z } from "zod";

export const payrollWorkAdjustmentDto = z.object({
  id: z.uuid(),
  revision: z.number().int().min(1).max(Number.MAX_SAFE_INTEGER),
  workDate: z.iso.date(),
  recordedAt: z.iso.datetime({ offset: true }),
  reason: z.string().min(1).max(1000),
});
export const payrollWorkDaysDto = z
  .array(
    z.object({
      date: z.iso.date(),
      schedule: z.enum(["WORK", "OFF", "UNASSIGNED"]),
      attendance: z.enum(["WORKED", "ABSENCE_RECORDED", "UNRECORDED", "OFF", "UNASSIGNED"]),
      officialHoliday: z.boolean(),
      overtime: z
        .array(
          z.object({
            requestId: z.uuid(),
            revision: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
            minutes: z.number().int().min(1).max(1440),
          }),
        )
        .max(24),
    }),
  )
  .min(28)
  .max(31);
export type PayrollWorkAdjustmentDto = z.infer<typeof payrollWorkAdjustmentDto>;
export type PayrollWorkDaysDto = z.infer<typeof payrollWorkDaysDto>;
