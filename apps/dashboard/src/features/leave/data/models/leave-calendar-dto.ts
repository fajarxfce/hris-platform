import { z } from "zod";
import { leaveDateDto, leaveVersionDto } from "./leave-value-dto";

const scope = z.enum(["COMPANY", "TEAM"]);
const count = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const timezone = z
  .string()
  .min(1)
  .max(64)
  .refine((value) => {
    try {
      new Intl.DateTimeFormat("en", { timeZone: value });
      return true;
    } catch {
      return false;
    }
  });
export const leaveCalendarDto = z.object({
  month: z.string().length(7),
  scope,
  companyDate: leaveDateDto,
  timezone,
  days: z
    .array(
      z.object({
        date: leaveDateDto,
        employeeCount: count.min(1),
        requestCount: count.min(1),
        pendingCount: count,
        approvedCount: count,
        cancellationPendingCount: count,
      }),
    )
    .max(31),
});
export const leaveCalendarEntriesDto = z.object({
  date: leaveDateDto,
  scope,
  companyDate: leaveDateDto,
  timezone,
  entries: z.object({
    items: z
      .array(
        z.object({
          requestId: z.uuid(),
          employeeId: z.uuid(),
          employeeNumber: z.string().min(1).max(32),
          employeeName: z.string().min(1).max(200),
          typeCode: z.string().min(1).max(32),
          typeName: z.string().min(1).max(200),
          portion: z.enum(["FULL", "FIRST_HALF", "SECOND_HALF"]),
          status: z.enum(["PENDING", "APPROVED", "CANCELLATION_PENDING"]),
          version: leaveVersionDto,
        }),
      )
      .max(20),
    nextCursor: z.uuid().nullable(),
  }),
});
export type LeaveCalendarScopeDto = z.infer<typeof scope>;
export type LeaveCalendarDto = z.infer<typeof leaveCalendarDto>;
export type LeaveCalendarEntriesDto = z.infer<typeof leaveCalendarEntriesDto>;
