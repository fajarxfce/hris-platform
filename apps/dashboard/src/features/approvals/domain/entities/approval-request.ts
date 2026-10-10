import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";

export type ApprovalId = string & { readonly approvalId: unique symbol };
export const approvalKinds = [
  "LEAVE",
  "LEAVE_CANCELLATION",
  "EXPENSE",
  "OVERTIME",
  "PAYROLL",
] as const;
export type ApprovalKind = (typeof approvalKinds)[number];
export const approvalStatuses = [
  "PENDING",
  "BLOCKED",
  "APPROVED",
  "REJECTED",
  "CANCELLED",
] as const;
export type ApprovalStatus = (typeof approvalStatuses)[number];

export type ApprovalRequest = Readonly<{
  id: ApprovalId;
  companyId: CompanyId;
  kind: ApprovalKind;
  resourceId: string;
  authorId: AccountId;
  requesterId: AccountId | null;
  templateId: string;
  templateRevision: number;
  stages: readonly Readonly<{ assignees: readonly AccountId[] }>[];
  currentStep: number;
  status: ApprovalStatus;
  version: number;
  submittedAt: string;
  excludedAccountIds: readonly AccountId[];
}>;

export type ApprovalInbox = Readonly<{
  items: readonly ApprovalRequest[];
  nextCursor: string | null;
}>;
