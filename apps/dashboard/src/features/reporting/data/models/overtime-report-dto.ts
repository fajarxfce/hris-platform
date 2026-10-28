import { z } from "zod";
import { overtimeStatuses } from "../../../workforce/domain/entities/overtime-request";

const count = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
export const overtimeReportCountsDto = z.object({
  requests: count,
  employments: count,
  actualRequests: count,
  requestedMinutes: count,
  actualMinutes: count,
  approvedMinutes: count,
});
export const overtimeReportDto = z.object({
  companyId: z.uuid(),
  from: z.iso.date(),
  until: z.iso.date(),
  status: z.enum(overtimeStatuses).nullish(),
  evaluatedAt: z.iso.datetime(),
  definitionVersion: z.literal("overtime.v1"),
  totals: overtimeReportCountsDto,
  months: z
    .array(
      z.object({
        month: z.string().regex(/^\d{4}-\d{2}$/u),
        from: z.iso.date(),
        until: z.iso.date(),
        counts: overtimeReportCountsDto,
      }),
    )
    .min(1)
    .max(13),
});
export type OvertimeReportDto = z.infer<typeof overtimeReportDto>;
