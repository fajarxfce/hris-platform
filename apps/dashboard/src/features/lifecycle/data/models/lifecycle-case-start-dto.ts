import { z } from "zod";

export type LifecycleCaseStartDto = {
  id: string;
  employmentId: string;
  templateId: string;
  templateVersion: number;
  targetDate: string;
  assignees: Record<string, string>;
  reason: string;
};
export const lifecycleCaseStartReceiptDto = z.object({ id: z.uuid(), version: z.literal(0) });
export type LifecycleCaseStartReceiptDto = z.infer<typeof lifecycleCaseStartReceiptDto>;
