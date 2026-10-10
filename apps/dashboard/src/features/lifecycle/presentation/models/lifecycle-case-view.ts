import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { LifecycleCase, LifecycleCasePage } from "../../domain/entities/lifecycle-case";
import type {
  AssignedLifecycleTaskPage,
  LifecycleTaskContext,
} from "../../domain/entities/lifecycle-task-context";
import { lifecycleCaseMessages } from "../i18n/lifecycle-case-messages";

export function lifecycleCasesView(page: LifecycleCasePage, locale: Locale) {
  const text = lifecycleCaseMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  const number = new Intl.NumberFormat(locale);
  return page.items.map((item) => ({
    id: item.id,
    actionLabel: `${item.employee.name} · ${item.employee.employeeNumber}`,
    cells: [
      item.employee.name,
      item.employee.employeeNumber,
      text[item.kind],
      text[item.status],
      date.format(new Date(`${item.targetDate}T00:00:00Z`)),
      `${number.format(item.tasks.filter((task) => task.status !== "PENDING").length)} / ${number.format(item.tasks.length)}`,
    ],
  }));
}
export function lifecycleCaseView(details: LifecycleCase, locale: Locale, timezone: string) {
  const text = lifecycleCaseMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  const instant = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  const number = new Intl.NumberFormat(locale);
  return {
    properties: [
      { label: text.employee, value: details.employee.name },
      { label: text.employeeNumber, value: details.employee.employeeNumber },
      { label: text.kind, value: text[details.kind] },
      { label: text.status, value: text[details.status] },
      { label: text.targetDate, value: date.format(new Date(`${details.targetDate}T00:00:00Z`)) },
      { label: text.template, value: details.templateName },
      { label: text.templateVersion, value: number.format(details.templateVersion) },
      { label: text.version, value: number.format(details.version) },
      {
        label: `${text.createdAt} (${timezone})`,
        value: instant.format(new Date(details.createdAt)),
      },
    ],
    tasks: details.tasks.map((task) => ({
      id: task.key,
      actionLabel: task.title,
      cells: [
        task.title,
        text[task.status],
        task.required ? text.required : text.optional,
        date.format(new Date(`${task.dueDate}T00:00:00Z`)),
      ],
    })),
  };
}
export function assignedLifecycleTasksView(page: AssignedLifecycleTaskPage, locale: Locale) {
  const text = lifecycleCaseMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  return page.items.map((item) => ({
    id: `${item.caseId}:${item.task.key}`,
    actionLabel: `${item.task.title} · ${item.employee.name}`,
    cells: [
      item.task.title,
      item.employee.name,
      item.employee.employeeNumber,
      text[item.kind],
      date.format(new Date(`${item.task.dueDate}T00:00:00Z`)),
    ],
  }));
}
export function lifecycleTaskView(
  context: LifecycleTaskContext,
  account: AccountId,
  locale: Locale,
  timezone: string,
) {
  const text = lifecycleCaseMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  const instant = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  const task = context.task;
  return [
    { label: text.employee, value: context.employee.name },
    { label: text.employeeNumber, value: context.employee.employeeNumber },
    { label: text.kind, value: text[context.kind] },
    { label: text.key, value: task.key },
    { label: text.status, value: text[task.status] },
    { label: text.requirement, value: task.required ? text.required : text.optional },
    { label: text.dueDate, value: date.format(new Date(`${task.dueDate}T00:00:00Z`)) },
    {
      label: text.assignee,
      value: task.assigneeId === account ? text.you : (task.assigneeId ?? text.none),
    },
    { label: text.version, value: new Intl.NumberFormat(locale).format(context.caseVersion) },
    ...(task.completedAt === null
      ? []
      : [
          {
            label: text.completedBy,
            value: task.completedBy === account ? text.you : (task.completedBy ?? text.none),
          },
          {
            label: `${text.completedAt} (${timezone})`,
            value: instant.format(new Date(task.completedAt)),
          },
        ]),
  ];
}
