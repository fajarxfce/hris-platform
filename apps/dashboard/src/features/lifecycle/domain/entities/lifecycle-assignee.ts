import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";

export type LifecycleAssignee = Readonly<{
  id: AccountId;
  companyId: CompanyId;
  displayName: string;
}>;
export type LifecycleAssigneePage = Readonly<{
  items: readonly LifecycleAssignee[];
  nextCursor: string | null;
}>;
