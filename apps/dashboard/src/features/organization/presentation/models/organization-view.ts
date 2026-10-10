import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { OrganizationUnit } from "../../domain/entities/organization-unit";
import type { OrganizationUnitPage } from "../../domain/entities/organization-unit-page";
import { organizationMessages } from "../i18n/organization-messages";

export function organizationDirectoryView(page: OrganizationUnitPage, locale: Locale) {
  const text = organizationMessages(locale);
  return page.items.map((unit) => ({
    id: unit.id,
    actionLabel: `${unit.name} (${unit.code})`,
    cells: [unit.code, unit.name, text[unit.kind], unit.active ? text.active : text.inactive],
  }));
}

export function organizationUnitView(unit: OrganizationUnit, locale: Locale) {
  const text = organizationMessages(locale);
  return [
    { label: text.code, value: unit.code },
    { label: text.name, value: unit.name },
    { label: text.kind, value: text[unit.kind] },
    { label: text.status, value: unit.active ? text.active : text.inactive },
    ...(unit.kind === "BRANCH"
      ? [{ label: text.timezone, value: unit.timezone ?? text.none }]
      : []),
    { label: text.version, value: new Intl.NumberFormat(locale).format(unit.version) },
  ];
}
