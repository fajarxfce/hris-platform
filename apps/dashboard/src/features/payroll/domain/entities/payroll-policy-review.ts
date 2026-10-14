import type { PayrollPolicy } from "./payroll-policy";
import type { PayrollPolicyRevision } from "./payroll-policy-revision";

export type PayrollPolicyReview = Readonly<{
  asOf: string;
  current: PayrollPolicy | null;
  effective: PayrollPolicy | null;
  history: Readonly<{ items: readonly PayrollPolicyRevision[]; nextCursor: string | null }>;
}>;
