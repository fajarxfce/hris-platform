import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { ManagedAccount } from "../../domain/entities/managed-account";
import { companyMembershipCreationMessages } from "../i18n/company-membership-creation-messages";
import { managedAccountMessages } from "../i18n/managed-account-messages";

export function companyAccountCandidateRows(
  accounts: readonly ManagedAccount[],
  accountId: string,
  locale: Locale,
) {
  const text = managedAccountMessages(locale);
  return accounts.map((account) => ({
    id: account.id,
    actionLabel: account.displayName,
    actionDisabled: !account.active || account.invitationPending || account.id === accountId,
    cells: [
      account.displayName,
      account.email,
      account.id === accountId
        ? companyMembershipCreationMessages(locale).current
        : account.invitationPending
          ? text.pending
          : account.active
            ? text.active
            : text.inactive,
    ],
  }));
}
