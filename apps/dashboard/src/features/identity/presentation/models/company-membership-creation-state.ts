import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { CompanyMembershipCandidate } from "../../domain/entities/company-membership-candidate";

export type CompanyMembershipCreationState = Readonly<{
  stage: "loading" | "unavailable" | "editing" | "saving" | "unconfirmed" | "conflict" | "saved";
  candidate: CompanyMembershipCandidate | null;
  failure: Failure | null;
  operationId: string | null;
  receipt: MutationReceipt | null;
}>;
export const initialCompanyMembershipCreationState: CompanyMembershipCreationState = {
  stage: "loading",
  candidate: null,
  failure: null,
  operationId: null,
  receipt: null,
};
