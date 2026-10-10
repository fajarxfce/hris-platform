import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { LifecycleTemplate } from "../../domain/entities/lifecycle-template";
import type { LifecycleTemplatePage } from "../../domain/entities/lifecycle-template-page";
import { lifecycleMessages } from "../i18n/lifecycle-messages";

export function lifecycleTemplatesView(page: LifecycleTemplatePage, locale: Locale) {
  const text = lifecycleMessages(locale);
  const number = new Intl.NumberFormat(locale);
  return page.items.map((template) => ({
    id: template.id,
    actionLabel: `${template.name} (${template.code})`,
    cells: [
      template.code,
      template.name,
      text[template.kind],
      template.active ? text.active : text.inactive,
      number.format(template.tasks.length),
    ],
  }));
}
export function lifecycleTemplateView(template: LifecycleTemplate, locale: Locale) {
  const text = lifecycleMessages(locale);
  const number = new Intl.NumberFormat(locale);
  return {
    properties: [
      { label: text.code, value: template.code },
      { label: text.kind, value: text[template.kind] },
      { label: text.status, value: template.active ? text.active : text.inactive },
      { label: text.version, value: number.format(template.version) },
    ],
    tasks: template.tasks.map((task) => ({
      id: task.key,
      cells: [
        task.key,
        task.title,
        task.required ? text.required : text.optional,
        number.format(task.dueDays),
      ],
    })),
  };
}
