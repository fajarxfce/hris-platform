export type OvertimeDecisionDto = {
  expectedVersion: number;
  decision: "APPROVE" | "REJECT";
  reason: string;
};
