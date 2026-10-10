import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { LifecycleTemplate } from "../../domain/entities/lifecycle-template";
import { lifecycleMessages } from "../i18n/lifecycle-messages";

export function lifecycleCaseTemplatePreview(
  template: LifecycleTemplate,
  targetDate: string,
  locale: Locale,
) {
  const text = lifecycleMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  const validDate =
    isCalendarDate(targetDate) && targetDate >= "1900-01-01" && targetDate <= "2200-12-31";
  return template.tasks.map((task) => {
    let dueDate = "—";
    if (validDate) {
      const due = new Date(`${targetDate}T00:00:00.000Z`);
      due.setUTCDate(due.getUTCDate() + task.dueDays);
      dueDate = date.format(due);
    }
    return {
      id: task.key,
      cells: [task.title, task.required ? text.required : text.optional, dueDate],
    };
  });
}
