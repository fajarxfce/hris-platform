import type { PayrollPolicy } from "./payroll-policy";

export type PayrollPolicyRevision = Readonly<{
  policy: PayrollPolicy;
  actorId: string;
  reason: string;
  recordedAt: string;
}>;
