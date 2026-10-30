import { z } from "zod";
import { approvalRequestDto } from "./approval-request-dto";

const step = z.number().int().min(0).max(7);
const revision = z.number().int().min(1).max(Number.MAX_SAFE_INTEGER);
const instant = z.iso.datetime({ offset: true });
export const approvalHistoryDto = z.strictObject({
  request: approvalRequestDto,
  decisions: z
    .array(
      z.strictObject({
        step,
        actorId: z.uuid(),
        decidingFor: z.uuid(),
        decision: z.enum(["APPROVE", "REJECT"]),
        reason: z.string().max(1000),
        decidedAt: instant,
      }),
    )
    .max(8),
  reassignments: z.strictObject({
    items: z
      .array(
        z.strictObject({
          revision,
          step,
          assignees: z.array(z.uuid()).min(1).max(25),
          actorId: z.uuid(),
          reason: z.string().max(1000),
          recordedAt: instant,
        }),
      )
      .max(50),
    nextCursor: z
      .string()
      .regex(/^[1-9][0-9]{0,15}$/u)
      .nullable(),
  }),
});
export type ApprovalHistoryDto = z.infer<typeof approvalHistoryDto>;
