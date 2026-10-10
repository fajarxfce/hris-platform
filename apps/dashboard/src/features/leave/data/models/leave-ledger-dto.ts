import { z } from "zod";
import { leaveBalanceSummaryDto, leaveEmployeeReferenceDto } from "./leave-balance-dto";
import { leaveInstantDto } from "./leave-value-dto";

const delta = z
  .string()
  .max(13)
  .regex(/^-?(?:0|[1-9]\d{0,9})(?:\.5)?$/u)
  .refine(
    (value) =>
      value !== "-0" && Number(value) * 2 >= -2_147_483_648 && Number(value) * 2 <= 2_147_483_647,
  );
export const leaveLedgerEntryDto = z.object({
  id: z.uuid(),
  kind: z.enum([
    "ADJUSTMENT",
    "GRANT",
    "RESERVE",
    "CONSUME",
    "RELEASE",
    "REFUND",
    "EXPIRE",
    "CARRY_OUT",
    "CARRY_IN",
  ]),
  sourceId: z.uuid(),
  requestId: z.uuid().nullable(),
  actorId: z.uuid(),
  availableDeltaDays: delta,
  reservedDeltaDays: delta,
  consumedDeltaDays: delta,
  recordedAt: leaveInstantDto,
  reason: z.string().max(1000),
});
export const leaveLedgerDto = leaveBalanceSummaryDto.extend({
  employee: leaveEmployeeReferenceDto,
  availableActions: z
    .array(
      z
        .string()
        .max(64)
        .regex(/^[A-Z][A-Z_]*$/u),
    )
    .max(16)
    .default([]),
  entries: z.object({
    items: z.array(leaveLedgerEntryDto).max(20),
    nextCursor: z.uuid().nullable(),
  }),
});
export type LeaveLedgerDto = z.infer<typeof leaveLedgerDto>;
