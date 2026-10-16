import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { OidcBinding } from "../../domain/entities/oidc-binding";
import { managedAccountMessages } from "../i18n/managed-account-messages";
import { oidcBindingMessages } from "../i18n/oidc-binding-messages";

export function oidcBindingRows(bindings: readonly OidcBinding[], locale: Locale) {
  const text = oidcBindingMessages(locale);
  const time = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" });
  return bindings.map((binding) => ({
    id: binding.id,
    actionLabel: binding.subject,
    cells: [
      binding.subject,
      binding.issuer,
      binding.active ? text.active : text.inactive,
      time.format(new Date(binding.linkedAt)),
    ],
  }));
}
export function oidcBindingProperties(binding: OidcBinding, locale: Locale) {
  return [
    {
      label: managedAccountMessages(locale).basedOn,
      value: new Intl.NumberFormat(locale).format(binding.version),
    },
    {
      label: oidcBindingMessages(locale).linkedAt,
      value: new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(
        new Date(binding.linkedAt),
      ),
    },
  ];
}
