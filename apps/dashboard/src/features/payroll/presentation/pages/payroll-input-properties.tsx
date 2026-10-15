import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { payrollInputMessages } from "../i18n/payroll-input-messages";
import type { payrollInputTermsView, payrollInputView } from "../models/payroll-input-view";
export function PayrollInputProperties({
  view,
  locale,
}: {
  view: ReturnType<typeof payrollInputView>;
  locale: Locale;
}) {
  return (
    <div className="app-compensation-properties">
      <AppPropertyList title={view.title} items={view.properties} />
      <PayrollInputTermsProperties view={view} locale={locale} />
    </div>
  );
}

export function PayrollInputTermsProperties({
  view,
  locale,
}: {
  view: ReturnType<typeof payrollInputTermsView>;
  locale: Locale;
}) {
  const text = payrollInputMessages(locale);
  return (
    <>
      {view.earnings.length ? (
        <AppResourceTable
          title={text.earnings}
          columns={[
            { id: "code", label: text.code },
            { id: "name", label: text.name },
            { id: "amount", label: text.amount, numeric: true },
            { id: "taxable", label: text.taxable },
          ]}
          rows={view.earnings}
        />
      ) : (
        <p className="app-muted">{text.noEarnings}</p>
      )}
      {view.deductions.length ? (
        <AppResourceTable
          title={text.deductions}
          columns={[
            { id: "code", label: text.code },
            { id: "name", label: text.name },
            { id: "amount", label: text.amount, numeric: true },
          ]}
          rows={view.deductions}
        />
      ) : (
        <p className="app-muted">{text.noDeductions}</p>
      )}
      {view.days.length ? (
        <AppResourceTable
          title={text.days}
          columns={[
            { id: "date", label: text.workDate },
            { id: "portion", label: text.portion },
            { id: "disposition", label: text.disposition },
            { id: "reference", label: text.reference },
          ]}
          rows={view.days}
        />
      ) : (
        <p className="app-muted">{text.noDays}</p>
      )}
      {view.holiday ? (
        <AppPropertyList title={text.holiday} items={view.holiday} />
      ) : (
        <p className="app-muted">{text.noHoliday}</p>
      )}
    </>
  );
}
