import { z } from "zod";

export const approvalAssigneePageDto = z.object({
  items: z.array(z.object({ id: z.uuid(), displayName: z.string().min(1).max(200) })).max(10),
  nextCursor: z.uuid().nullable(),
});
export type ApprovalAssigneePageDto = z.infer<typeof approvalAssigneePageDto>;
export type ApprovalAssigneeSearchDto = Readonly<{
  kind: string;
  query: string;
  after: string | null;
}>;
