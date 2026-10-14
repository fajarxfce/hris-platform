import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { payrollCalculationMessages } from "../i18n/payroll-calculation-messages";
import type { payrollCalculationOutputView } from "../models/payroll-calculation-output-view";

export function PayrollCalculationOutputSection({
  view,
  locale,
}: {
  view: ReturnType<typeof payrollCalculationOutputView>;
  locale: Locale;
}) {
  const text = payrollCalculationMessages(locale);
  return (
    <>
      {view.groups.map((group) => (
        <AppPropertyList key={group.title} title={group.title} items={group.items} />
      ))}
      {view.earnings.length > 0 && (
        <AppResourceTable
          title={text.earnings}
          columns={[
            { id: "type", label: text.kind },
            { id: "code", label: text.code },
            { id: "name", label: text.name },
            { id: "amount", label: text.amount, numeric: true },
            { id: "taxable", label: text.taxable },
          ]}
          rows={view.earnings}
        />
      )}
      {view.contributions.length > 0 && (
        <AppResourceTable
          title={text.contributions}
          columns={[
            { id: "program", label: text.program },
            { id: "base", label: text.base, numeric: true },
            { id: "employee", label: text.employeeAmount, numeric: true },
            { id: "employer", label: text.employerAmount, numeric: true },
          ]}
          rows={view.contributions}
        />
      )}
      {view.overtime.length > 0 && (
        <AppResourceTable
          title={text.overtime}
          columns={[
            { id: "date", label: text.date },
            { id: "type", label: text.dayKind },
            { id: "wage", label: text.hourlyWage, numeric: true },
            { id: "amount", label: text.amount, numeric: true },
          ]}
          rows={view.overtime}
        />
      )}
    </>
  );
}
