import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { CompanyMemberGrant } from "../../domain/entities/company-member";

export type CompanyMembershipEditorState = Readonly<{
  stage: "loading" | "unavailable" | "editing" | "saving" | "unconfirmed" | "conflict" | "saved";
  grant: CompanyMemberGrant | null;
  permissions: readonly string[];
  failure: Failure | null;
  operationId: string | null;
  receipt: MutationReceipt | null;
}>;
export const initialCompanyMembershipEditorState: CompanyMembershipEditorState = {
  stage: "loading",
  grant: null,
  permissions: [],
  failure: null,
  operationId: null,
  receipt: null,
};
