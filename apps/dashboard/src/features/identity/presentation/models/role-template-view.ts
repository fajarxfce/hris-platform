import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { RoleTemplatePage } from "../../domain/entities/role-template";
import type { RoleTemplateReview } from "../../domain/entities/role-template-review";
import type { RoleTemplateRevision } from "../../domain/entities/role-template-revision";
import { permissionGroupLabel, permissionLabel } from "../i18n/permission-messages";
import { roleTemplateMessages } from "../i18n/role-template-messages";

export function rolePermissionGroups(
  permissions: readonly string[],
  selected: readonly string[],
  locale: Locale,
) {
  const groups = new Map<
    string,
    { id: string; label: string; items: { id: string; label: string; checked: boolean }[] }
  >();
  const chosen = new Set(selected);
  for (const permission of permissions) {
    const id = permission.split(".")[0] ?? permission;
    const group = groups.get(id) ?? { id, label: permissionGroupLabel(id, locale), items: [] };
    group.items.push({
      id: permission,
      label: permissionLabel(permission, locale),
      checked: chosen.has(permission),
    });
    groups.set(id, group);
  }
  const collator = new Intl.Collator(locale);
  const count = new Intl.NumberFormat(locale);
  const text = roleTemplateMessages(locale);
  return [...groups.values()]
    .sort((a, b) => collator.compare(a.label, b.label))
    .map((group) => ({
      ...group,
      selected: `${count.format(group.items.filter((item) => item.checked).length)} ${text.selected}`,
      items: group.items.sort((a, b) => collator.compare(a.label, b.label)),
    }));
}
export function roleTemplateRows(page: RoleTemplatePage | null, locale: Locale) {
  const text = roleTemplateMessages(locale);
  const number = new Intl.NumberFormat(locale);
  return (page?.items ?? []).map((role) => ({
    id: role.id,
    actionLabel: role.name,
    cells: [
      role.name,
      role.code,
      role.active ? text.active : text.inactive,
      number.format(role.permissions.length),
      number.format(role.version),
    ],
  }));
}
export function roleTemplateView(review: RoleTemplateReview, locale: Locale, timezone: string) {
  const text = roleTemplateMessages(locale);
  const number = new Intl.NumberFormat(locale);
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  const current = review.current;
  return {
    properties: [
      { label: text.name, value: current.name },
      { label: text.code, value: current.code },
      { label: text.status, value: current.active ? text.active : text.inactive },
      { label: text.version, value: number.format(current.version) },
    ],
    permissions: rolePermissionGroups(current.permissions, current.permissions, locale),
    history: review.history.items.map((row) => ({
      id: String(row.revision),
      actionLabel: `${text.revision} ${number.format(row.revision)}`,
      cells: [
        number.format(row.revision),
        row.name,
        row.active ? text.active : text.inactive,
        time.format(new Date(row.recordedAt)),
        row.reason,
      ],
    })),
  };
}
export function roleTemplateRevisionView(
  revision: RoleTemplateRevision,
  locale: Locale,
  timezone: string,
) {
  const text = roleTemplateMessages(locale);
  return {
    title: `${text.revision} ${new Intl.NumberFormat(locale).format(revision.revision)}`,
    properties: [
      { label: text.name, value: revision.name },
      { label: text.code, value: revision.code },
      { label: text.status, value: revision.active ? text.active : text.inactive },
      { label: text.reason, value: revision.reason },
      { label: text.actor, value: revision.actorId },
      {
        label: `${text.recorded} (${timezone})`,
        value: new Intl.DateTimeFormat(locale, {
          dateStyle: "medium",
          timeStyle: "short",
          timeZone: timezone,
        }).format(new Date(revision.recordedAt)),
      },
    ],
    permissions: rolePermissionGroups(revision.permissions, revision.permissions, locale),
  };
}
