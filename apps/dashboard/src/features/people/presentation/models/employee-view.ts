import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { Employee } from "../../domain/entities/employee";
import type { EmployeePage } from "../../domain/entities/employee-page";
import type { EmploymentTerms } from "../../domain/entities/employment-terms";
import { peopleMessages } from "../i18n/people-messages";

export function employeeDirectoryView(page: EmployeePage, locale: Locale) {
  const text = peopleMessages(locale);
  return page.items.map((employee) => ({
    id: employee.id,
    actionLabel: `${employee.legalName} (${employee.employeeNumber})`,
    cells: [
      employee.employeeNumber,
      employee.legalName,
      text[employee.terms.status],
      text[employee.terms.contract],
    ],
  }));
}

export function employmentTermsItems(terms: EmploymentTerms, locale: Locale) {
  const text = peopleMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  return [
    { label: text.status, value: text[terms.status] },
    { label: text.contract, value: text[terms.contract] },
    { label: text.start, value: date.format(new Date(`${terms.startDate}T00:00:00Z`)) },
    {
      label: text.end,
      value:
        terms.endDate === null ? text.none : date.format(new Date(`${terms.endDate}T00:00:00Z`)),
    },
    { label: text.effectiveFrom, value: date.format(new Date(`${terms.effectiveFrom}T00:00:00Z`)) },
  ];
}

export function employeeDetailsView(employee: Employee, asOf: string, locale: Locale) {
  const text = peopleMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  const number = new Intl.NumberFormat(locale);
  return [
    { label: text.number, value: employee.employeeNumber },
    { label: text.name, value: employee.legalName },
    { label: text.email, value: employee.email ?? text.none },
    { label: text.asOf, value: date.format(new Date(`${asOf}T00:00:00Z`)) },
    ...employmentTermsItems(employee.terms, locale),
    { label: text.applied, value: number.format(employee.appliedRevision) },
    { label: text.version, value: number.format(employee.version) },
  ];
}
