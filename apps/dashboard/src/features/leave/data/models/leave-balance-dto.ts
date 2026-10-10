import { z } from "zod";
import { leaveVersionDto } from "./leave-value-dto";

export const leaveBalanceQuantityDto = z
  .string()
  .max(13)
  .regex(/^(?:0|[1-9]\d{0,9})(?:\.5)?$/u)
  .refine((value) => Number(value) * 2 <= 2_147_483_647);
export const leaveEmployeeReferenceDto = z.object({
  id: z.uuid(),
  number: z.string().min(2).max(32).nullable(),
  name: z.string().min(1).max(200).nullable(),
});
export const leaveBalanceDto = z.object({
  accountId: z.uuid().nullable(),
  year: z.number().int().min(1900).max(2200),
  availableDays: leaveBalanceQuantityDto,
  reservedDays: leaveBalanceQuantityDto,
  consumedDays: leaveBalanceQuantityDto,
  version: leaveVersionDto,
  closed: z.boolean(),
});
export const leaveBalanceSummaryDto = z.object({
  typeId: z.uuid(),
  typeCode: z.string().regex(/^[A-Z][A-Z0-9_-]{0,31}$/u),
  typeName: z.string().min(1).max(200),
  balance: leaveBalanceDto,
});
export const employeeLeaveBalancesDto = z.object({
  employee: leaveEmployeeReferenceDto,
  year: z.number().int().min(1900).max(2200),
  balances: z.object({
    items: z.array(leaveBalanceSummaryDto).max(20),
    nextCursor: z
      .string()
      .regex(/^[A-Z][A-Z0-9_-]{0,31}$/u)
      .nullable(),
  }),
});
export type LeaveBalanceDto = z.infer<typeof leaveBalanceDto>;
export type EmployeeLeaveBalancesDto = z.infer<typeof employeeLeaveBalancesDto>;
