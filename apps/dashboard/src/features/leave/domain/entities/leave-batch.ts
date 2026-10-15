import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeavePolicyTerms } from "./leave-policy-terms";
export const leaveBatchKinds = ["ACCRUAL", "YEAR_CLOSE"] as const;
export type LeaveBatchKind = (typeof leaveBatchKinds)[number];
export type LeaveBatchId = string & { readonly leaveBatchId: unique symbol };
export type LeaveBatchPolicy = Readonly<
  LeavePolicyTerms & { typeId: string; code: string; revision: number }
>;
export type LeaveBatch = Readonly<{
  id: LeaveBatchId;
  companyId: CompanyId;
  kind: LeaveBatchKind;
  typeId: string;
  period: string;
  policy: LeaveBatchPolicy;
  policyVersion: number;
  timezone: string;
  actorId: string;
  createdAt: string;
  reason: string;
  totalEmployees: number;
  jobId: string;
  status: "RUNNING" | "STOPPED" | "COMPLETED";
  version: number;
}>;
export type LeaveBatchPage = Readonly<{ items: readonly LeaveBatch[]; nextCursor: string | null }>;
