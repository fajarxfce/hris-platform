import type { AccountId } from "../../../../core/domain/identifiers";

export type ApprovalAssignee = Readonly<{ id: AccountId; displayName: string }>;
export type ApprovalAssigneePage = Readonly<{
  items: readonly ApprovalAssignee[];
  nextCursor: string | null;
}>;
export type ApprovalAssigneeSearch = Readonly<{
  kind: string;
  query: string;
  after: string | null;
}>;
