import type { Failure } from "../../../../core/domain/result";
import type { LeavePolicyPage } from "../../domain/entities/leave-policy-definition";

export type LeavePoliciesState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: LeavePolicyPage | null;
  failure: Failure | null;
}>;
export const initialLeavePoliciesState: LeavePoliciesState = {
  stage: "loading",
  page: null,
  failure: null,
};
