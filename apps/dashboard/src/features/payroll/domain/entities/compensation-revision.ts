import type { EmployeeCompensation } from "./employee-compensation";
export type CompensationRevision = Readonly<{
  compensation: EmployeeCompensation;
  actorId: string;
  reason: string;
  recordedAt: string;
}>;
