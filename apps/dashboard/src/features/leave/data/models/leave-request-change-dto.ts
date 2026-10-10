export type LeaveRequestChangeDto = Readonly<{ version: number; reason: string }>;
export type LeaveRequestDecisionDto = LeaveRequestChangeDto &
  Readonly<{ decision: "APPROVE" | "REJECT" }>;
