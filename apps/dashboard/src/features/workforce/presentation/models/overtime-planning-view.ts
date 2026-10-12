import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { OvertimePlanningReview } from "../../domain/entities/overtime-planning-review";
import type { WorkforceEmployee } from "../../domain/entities/workforce-employee";
import { overtimePlanningMessages } from "../i18n/overtime-planning-messages";

export function overtimePlanningView(review: OvertimePlanningReview, locale: Locale) {
  const text = overtimePlanningMessages(locale);
  const clock = new Intl.DateTimeFormat(locale, {
    timeZone: review.timezone,
    dateStyle: "medium",
    timeStyle: "short",
  });
  return {
    employee: `${review.employee.legalName} · ${review.employee.employeeNumber}`,
    properties: [
      {
        label: text.regularShift,
        value:
          review.schedule.kind === "WORK"
            ? `${review.schedule.shift.details.name} · ${clock.format(new Date(review.schedule.startsAt))} – ${clock.format(new Date(review.schedule.endsAt))}`
            : text.dayOff,
      },
      { label: text.timezone, value: review.timezone },
    ],
  };
}

export function overtimePlanningEmployeeRows(
  employees: readonly WorkforceEmployee[],
  locale: Locale,
) {
  const text = overtimePlanningMessages(locale);
  return employees.map((employee) => ({
    id: employee.id,
    actionLabel: `${employee.legalName} · ${employee.employeeNumber}`,
    cells: [`${employee.legalName} · ${employee.employeeNumber}`, text[employee.status]],
  }));
}
