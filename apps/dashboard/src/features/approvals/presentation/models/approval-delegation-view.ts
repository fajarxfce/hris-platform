import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type {
  ApprovalDelegation,
  ApprovalDelegationPage,
} from "../../domain/entities/approval-delegation";
import { approvalDelegationMessages } from "../i18n/approval-delegation-messages";
import { approvalMessages } from "../i18n/approval-messages";

export function approvalDelegationsView(
  page: ApprovalDelegationPage,
  account: AccountId,
  locale: Locale,
  timezone: string,
) {
  const text = approvalDelegationMessages(locale);
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  return page.items.map((item) => ({
    id: item.id,
    actionLabel: `${approvalMessages(locale)[item.kind]} · ${item.id}`,
    cells: [
      approvalMessages(locale)[item.kind],
      item.fromAccount === account ? text.you : item.fromAccount,
      item.toAccount === account ? text.you : item.toAccount,
      time.format(new Date(item.validFrom)),
      time.format(new Date(item.validUntil)),
      item.active ? text.enabled : text.disabled,
    ],
  }));
}
export function approvalDelegationView(item: ApprovalDelegation, locale: Locale, timezone: string) {
  const text = approvalDelegationMessages(locale);
  const approval = approvalMessages(locale);
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "long",
    timeZone: timezone,
  });
  return [
    { label: text.reference, value: item.id },
    { label: approval.kind, value: approval[item.kind] },
    { label: text.from, value: item.fromAccount },
    { label: text.to, value: item.toAccount },
    { label: `${text.starts} (${timezone})`, value: time.format(new Date(item.validFrom)) },
    { label: `${text.ends} (${timezone})`, value: time.format(new Date(item.validUntil)) },
    { label: text.setting, value: item.active ? text.enabled : text.disabled },
    { label: approval.version, value: new Intl.NumberFormat(locale).format(item.version) },
  ];
}
