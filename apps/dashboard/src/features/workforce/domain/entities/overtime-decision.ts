import type { OvertimeRequestChange } from "./overtime-request-change";

export type OvertimeDecision = OvertimeRequestChange & Readonly<{ decision: "APPROVE" | "REJECT" }>;
