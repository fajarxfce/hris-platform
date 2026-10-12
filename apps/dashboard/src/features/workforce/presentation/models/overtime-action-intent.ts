import type { OvertimeAction } from "../../domain/entities/overtime-request-details";

export const overtimeActionIntents = ["actual", "approve", "reject", "withdraw"] as const;
export type OvertimeActionIntent = (typeof overtimeActionIntents)[number];
export const overtimeIntentAction: Readonly<Record<OvertimeActionIntent, OvertimeAction>> = {
  actual: "SUBMIT_ACTUAL",
  approve: "APPROVE",
  reject: "REJECT",
  withdraw: "WITHDRAW",
};
