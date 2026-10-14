import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { payrollPolicyMessages } from "../i18n/payroll-policy-messages";
import type { payrollPolicyView } from "../models/payroll-policy-view";

export function PayrollPolicyProperties({
  view,
  title,
  locale,
}: {
  view: ReturnType<typeof payrollPolicyView>;
  title: string;
  locale: Locale;
}) {
  return (
    <div className="app-report-content">
      <AppPropertyList title={title} items={view.properties} />
      <section aria-label={payrollPolicyMessages(locale).references}>
        <h3>{payrollPolicyMessages(locale).references}</h3>
        <ol>
          {view.references.map((reference) => (
            <li key={reference} className="app-reference">
              <a href={reference} target="_blank" rel="noopener noreferrer">
                {reference}
              </a>
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
}
