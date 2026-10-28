import { z } from "zod";
import { leaveStatuses } from "../../../leave/domain/entities/leave-request";

const count = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const days = z
  .string()
  .max(21)
  .regex(/^(?:0|[1-9]\d{0,18})(?:\.5)?$/u);
const metadata = {
  companyId: z.uuid(),
  from: z.iso.date(),
  until: z.iso.date(),
  evaluatedAt: z.iso.datetime(),
  definitionVersion: z.literal("leave.v1"),
};
export const leaveReportCountsDto = z.object({ requests: count, employments: count, days });
export const leaveReportDto = z.object({
  ...metadata,
  totals: leaveReportCountsDto,
  statuses: z
    .array(z.object({ status: z.enum(leaveStatuses), counts: leaveReportCountsDto }))
    .length(5),
});
export const leaveReportRequestPageDto = z.object({
  ...metadata,
  status: z.enum(leaveStatuses).nullable(),
  items: z
    .array(
      z.object({
        id: z.uuid(),
        employeeId: z.uuid(),
        employeeNumber: z.string().min(1).max(32),
        employeeName: z.string().min(1).max(200),
        typeCode: z.string().min(1).max(32),
        typeName: z.string().min(1).max(200),
        status: z.enum(leaveStatuses),
        submittedAt: z.iso.datetime(),
        version: count,
        days,
        requestDays: days,
      }),
    )
    .max(50),
  nextCursor: z.string().min(1).max(200).nullable(),
});
export type LeaveReportDto = z.infer<typeof leaveReportDto>;
export type LeaveReportCountsDto = z.infer<typeof leaveReportCountsDto>;
export type LeaveReportRequestPageDto = z.infer<typeof leaveReportRequestPageDto>;
