import type { AccountId } from "../../../../core/domain/identifiers";
import type { ApprovalKind } from "./approval-request";

export type ApprovalDelegationChange = Readonly<{
  id: string;
  kind: ApprovalKind;
  fromAccount: AccountId;
  toAccount: AccountId;
  validFrom: string;
  validUntil: string;
  active: boolean;
  expectedVersion: number | null;
  reason: string;
}>;
