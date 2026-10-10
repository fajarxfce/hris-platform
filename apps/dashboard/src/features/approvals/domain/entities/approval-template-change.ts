import type { ApprovalKind } from "./approval-request";
import type { ApprovalStageRule } from "./approval-template";

export type ApprovalTemplateChange = Readonly<{
  id: string;
  name: string;
  kind: ApprovalKind;
  active: boolean;
  expectedVersion: number | null;
  effectiveFrom: string;
  category: string | null;
  minimumAmount: string;
  stages: readonly ApprovalStageRule[];
  reason: string;
}>;
