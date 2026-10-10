import { z } from "zod";

export type LifecycleTemplateChangeDto = Readonly<{
  expectedVersion: number | null;
  code: string;
  name: string;
  kind: "ONBOARDING" | "OFFBOARDING";
  active: boolean;
  reason: string;
  tasks: readonly Readonly<{ key: string; title: string; required: boolean; dueDays: number }>[];
}>;
export const lifecycleTemplateReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export type LifecycleTemplateReceiptDto = z.infer<typeof lifecycleTemplateReceiptDto>;
