import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { ManagedAccount } from "../../domain/entities/managed-account";
import { managedAccountMessages } from "../i18n/managed-account-messages";
export function platformPermissionLabel(permission: string, locale: Locale): string {
  const text = managedAccountMessages(locale);
  return permission === "identity.manage"
    ? text.manageAccounts
    : permission === "companies.create"
      ? text.createCompanies
      : permission;
}
export function managedAccountRows(accounts: readonly ManagedAccount[], locale: Locale) {
  const text = managedAccountMessages(locale);
  return accounts.map((account) => ({
    id: account.id,
    actionLabel: account.displayName,
    cells: [
      account.displayName,
      account.email,
      account.invitationPending ? text.pending : account.active ? text.active : text.inactive,
      account.mfaConfigured ? text.configured : text.notConfigured,
    ],
  }));
}
export function managedAccountProperties(account: ManagedAccount, locale: Locale) {
  const text = managedAccountMessages(locale);
  return [
    { label: text.email, value: account.email },
    {
      label: text.status,
      value: account.invitationPending
        ? text.pending
        : account.active
          ? text.active
          : text.inactive,
    },
    { label: text.mfa, value: account.mfaConfigured ? text.configured : text.notConfigured },
    { label: text.version, value: new Intl.NumberFormat(locale).format(account.version) },
    {
      label: text.permissions,
      value:
        account.platformPermissions
          .map((value) => platformPermissionLabel(value, locale))
          .join(", ") || text.none,
    },
  ];
}
