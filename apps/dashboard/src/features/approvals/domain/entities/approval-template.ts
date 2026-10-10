import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { ApprovalKind } from "./approval-request";

export type ApprovalTemplateId = string & { readonly approvalTemplateId: unique symbol };
export const approvalAssignments = ["MANAGER", "NAMED", "PERMISSION"] as const;
export type ApprovalAssignment = (typeof approvalAssignments)[number];
export type ApprovalStageRule = Readonly<{
  assignment: ApprovalAssignment;
  accountIds: readonly AccountId[];
  permission: string | null;
}>;
export type ApprovalTemplate = Readonly<{
  id: ApprovalTemplateId;
  companyId: CompanyId;
  name: string;
  kind: ApprovalKind;
  active: boolean;
  version: number;
  revision: number;
  effectiveFrom: string;
  category: string | null;
  minimumAmount: string;
  stages: readonly ApprovalStageRule[];
}>;
export type ApprovalTemplatePage = Readonly<{
  items: readonly ApprovalTemplate[];
  nextCursor: string | null;
}>;
export type ApprovalTemplateSearch = Readonly<{
  kind: string;
  asOf: string;
  after: string | null;
}>;
