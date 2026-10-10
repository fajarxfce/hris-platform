import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDateTimeInput } from "../../../../core/presentation/dates/company-date-time";
import type { ApprovalAssignee } from "../../domain/entities/approval-assignee";
import type { ApprovalDelegation } from "../../domain/entities/approval-delegation";
import type { ApprovalKind } from "../../domain/entities/approval-request";

export type ApprovalDelegationFormValues = {
  kind: ApprovalKind;
  fromAccount: ApprovalAssignee;
  toAccount: ApprovalAssignee | null;
  validFrom: string;
  validUntil: string;
  active: boolean;
  reason: string;
};
export function approvalDelegationFormValues(
  delegation: ApprovalDelegation | null,
  account: AccountId,
  timezone: string,
  now: string,
): ApprovalDelegationFormValues {
  const fromAccount = delegation?.fromAccount ?? account;
  return {
    kind: delegation?.kind ?? "LEAVE",
    fromAccount: { id: fromAccount, displayName: fromAccount },
    toAccount: delegation ? { id: delegation.toAccount, displayName: delegation.toAccount } : null,
    validFrom: companyDateTimeInput(delegation?.validFrom ?? now, timezone),
    validUntil: companyDateTimeInput(
      delegation?.validUntil ?? new Date(new Date(now).getTime() + 86_400_000).toISOString(),
      timezone,
    ),
    active: delegation?.active ?? true,
    reason: "",
  };
}
