import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { LeaveAccrualSchedule } from "../../domain/entities/leave-accrual-schedule";
import type { LeaveAccrualScheduleDetails } from "../../domain/entities/leave-accrual-schedule-details";
import type { LeaveAccrualScheduleReview } from "../../domain/entities/leave-accrual-schedule-review";
import { leaveAccrualScheduleMessages } from "../i18n/leave-accrual-schedule-messages";
import { leaveBatchMessages } from "../i18n/leave-batch-messages";
import { leaveBatchPeriodLabel } from "./leave-batch-view";
export function leaveAccrualSchedulesView(items: readonly LeaveAccrualSchedule[], locale: Locale) {
  const text = leaveBatchMessages(locale),
    number = new Intl.NumberFormat(locale);
  return items.map((item) => ({
    id: item.id,
    actionLabel: `${item.typeName} · ${leaveBatchPeriodLabel(item.firstPeriod, "ACCRUAL", locale)}`,
    cells: [
      item.typeName,
      text[item.frequency],
      leaveBatchPeriodLabel(item.firstPeriod, "ACCRUAL", locale),
      number.format(item.periodCount),
      number.format(item.totalEmployees),
      new Intl.DateTimeFormat(locale, {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: item.timezone,
      }).format(new Date(item.createdAt)),
    ],
  }));
}
export function leaveAccrualScheduleReviewView(review: LeaveAccrualScheduleReview, locale: Locale) {
  const text = leaveBatchMessages(locale),
    schedule = leaveAccrualScheduleMessages(locale),
    number = new Intl.NumberFormat(locale),
    instant = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: review.timezone,
    });
  return {
    properties: [
      { label: text.type, value: review.periods[0]?.type.name ?? "" },
      { label: text.frequency, value: text[review.frequency] },
      { label: schedule.employeeSelection, value: number.format(review.employeeIds.length) },
      { label: text.timezone, value: review.timezone },
    ],
    rows: review.periods.map((item) => ({
      id: item.period,
      cells: [
        leaveBatchPeriodLabel(item.period, "ACCRUAL", locale),
        instant.format(new Date(item.dueAt)),
        number.format(Number(item.type.accrual?.daysPerPeriod ?? "0")),
        item.type.name,
        number.format(item.type.minServiceMonths),
        item.type.allowedContracts.map((contract) => text[contract]).join(", "),
      ],
    })),
  };
}
export function leaveAccrualScheduleView(details: LeaveAccrualScheduleDetails, locale: Locale) {
  const header = details.schedule,
    text = leaveBatchMessages(locale),
    schedule = leaveAccrualScheduleMessages(locale),
    number = new Intl.NumberFormat(locale),
    instant = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: header.timezone,
    });
  return {
    properties: [
      { label: text.type, value: header.typeName },
      { label: text.frequency, value: text[header.frequency] },
      { label: schedule.employeeSelection, value: number.format(header.totalEmployees) },
      { label: text.createdAt, value: instant.format(new Date(header.createdAt)) },
      { label: text.timezone, value: header.timezone },
      { label: text.reason, value: header.reason },
    ],
    references: [
      { label: schedule.scheduleId, value: header.id },
      { label: schedule.author, value: header.actorId },
      { label: schedule.policyVersion, value: number.format(header.policyVersion) },
    ],
    rows: details.periods.map((item) => ({
      id: item.batch.id,
      actionLabel: leaveBatchPeriodLabel(item.batch.period, "ACCRUAL", locale),
      cells: [
        leaveBatchPeriodLabel(item.batch.period, "ACCRUAL", locale),
        instant.format(new Date(item.scheduledFor)),
        text[item.jobStatus],
        `${number.format(item.completedEmployees)} / ${number.format(header.totalEmployees)}`,
      ],
    })),
  };
}
