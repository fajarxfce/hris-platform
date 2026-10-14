import type { Failure } from "../../../../core/domain/result";
import type { PayrollPolicy } from "../../domain/entities/payroll-policy";
import type { PayrollPolicyReview } from "../../domain/entities/payroll-policy-review";
import type { PayrollPolicyRevision } from "../../domain/entities/payroll-policy-revision";

export type PayrollPolicyState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  review: PayrollPolicyReview | null;
  selected: Readonly<{ policy: PayrollPolicy; evidence: PayrollPolicyRevision | null }> | null;
  failure: Failure | null;
}>;
export const initialPayrollPolicyState: PayrollPolicyState = {
  stage: "loading",
  review: null,
  selected: null,
  failure: null,
};
