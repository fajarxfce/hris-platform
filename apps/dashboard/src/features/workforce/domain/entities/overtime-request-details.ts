import type { OvertimeHistory } from "./overtime-history";
import type { OvertimeRequest } from "./overtime-request";
import type { OvertimeWorkflow } from "./overtime-workflow";

export const overtimeActions = ["SUBMIT_ACTUAL", "WITHDRAW", "APPROVE", "REJECT"] as const;
export type OvertimeAction = (typeof overtimeActions)[number];
export type OvertimeRequestDetails = Readonly<{
  request: OvertimeRequest;
  approval: OvertimeWorkflow | null;
  history: OvertimeHistory;
  actions: readonly OvertimeAction[];
}>;
