import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { OrganizationUnitDetails } from "../../domain/entities/organization-unit-details";

export type OrganizationEditorState = Readonly<{
  stage: "loading" | "editing" | "conflict" | "saving" | "unconfirmed" | "saved" | "unavailable";
  details: OrganizationUnitDetails | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: OperationId | null;
}>;
export const initialOrganizationEditorState: OrganizationEditorState = {
  stage: "loading",
  details: null,
  failure: null,
  receipt: null,
  operationId: null,
};
