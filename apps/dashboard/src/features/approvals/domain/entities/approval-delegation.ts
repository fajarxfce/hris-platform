import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { ApprovalKind } from "./approval-request";

export type ApprovalDelegationId = string & { readonly __approvalDelegationId: unique symbol };
export type ApprovalDelegation = Readonly<{
  id: ApprovalDelegationId;
  companyId: CompanyId;
  kind: ApprovalKind;
  fromAccount: AccountId;
  toAccount: AccountId;
  validFrom: string;
  validUntil: string;
  active: boolean;
  version: number;
}>;
export type ApprovalDelegationPage = Readonly<{
  items: readonly ApprovalDelegation[];
  nextCursor: string | null;
}>;
