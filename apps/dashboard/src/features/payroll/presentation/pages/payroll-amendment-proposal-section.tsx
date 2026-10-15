import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { payrollAmendmentMessages } from "../i18n/payroll-amendment-messages";
import type { payrollAmendmentInspectionView } from "../models/payroll-amendment-view";
import { CompensationTermsProperties } from "./compensation-properties";
import { PayrollInputTermsProperties } from "./payroll-input-properties";
export function PayrollAmendmentProposalSection({
  view,
  locale,
}: {
  view: ReturnType<typeof payrollAmendmentInspectionView>;
  locale: Locale;
}) {
  const text = payrollAmendmentMessages(locale);
  return (
    <div className="app-amendment-proposal">
      <AppResourceTable
        title={text.sources}
        minWidth={600}
        columns={[
          { id: "source", label: text.source },
          { id: "revision", label: text.sourceRevision, numeric: true },
        ]}
        rows={view.sources}
      />
      <h2>{text.corrections}</h2>
      {view.corrections.map((row) => (
        <details key={row.id} className="app-amendment-disclosure">
          <summary>{row.label}</summary>
          <div className="app-amendment-evidence">
            <p className="app-reference">
              {text.source}: {row.id}
            </p>
            <h3>{text.compensation}</h3>
            {row.compensation ? (
              <CompensationTermsProperties view={row.compensation} locale={locale} />
            ) : (
              <p className="app-muted">{text.noCompensation}</p>
            )}
            <h3>{text.input}</h3>
            {row.input ? (
              <>
                <AppPropertyList title={text.input} items={row.inputProperties} />
                <PayrollInputTermsProperties view={row.input} locale={locale} />
              </>
            ) : (
              <p className="app-muted">{text.noInput}</p>
            )}
          </div>
        </details>
      ))}
    </div>
  );
}
