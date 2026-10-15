import { Link } from "react-router-dom";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollAmendmentCalculation } from "../../domain/entities/payroll-amendment-calculation";
import { payrollAmendmentMessages } from "../i18n/payroll-amendment-messages";
import type { payrollAmendmentInspectionView } from "../models/payroll-amendment-view";
import { PayrollCalculationOutputSection } from "./payroll-calculation-output-section";
export function PayrollAmendmentCalculationSection({
  calculation,
  view,
  locale,
  basisTo,
  onMonth,
}: {
  calculation: PayrollAmendmentCalculation | null;
  view: ReturnType<typeof payrollAmendmentInspectionView>;
  locale: Locale;
  basisTo: string | null;
  onMonth: (id: string) => void;
}) {
  const text = payrollAmendmentMessages(locale);
  return calculation ? (
    <>
      <AppPropertyList title={text.calculation} items={view.attempt} />
      <p className="app-muted">
        {text.calculationHint} {basisTo && <Link to={basisTo}>{text.basisLink}</Link>}
      </p>
      <AppFailure failure={calculation.summary.failure} locale={locale} />
      {calculation.preview && (
        <>
          <AppPropertyList title={text.totals} items={view.totals} />
          <AppResourceTable
            title={text.comparison}
            columns={[
              { id: "month", label: text.month },
              { id: "original", label: `${text.original} · ${text.takeHome}`, numeric: true },
              { id: "proposed", label: `${text.proposed} · ${text.takeHome}`, numeric: true },
              { id: "difference", label: text.difference, numeric: true },
            ]}
            rows={view.months}
            action={{ label: text.inspectMonth, onOpen: onMonth }}
          />
          {view.selectedMonth && (
            <section
              aria-label={`${text.month} ${view.selectedMonth}`}
              className="app-amendment-evidence"
            >
              <h2>{view.selectedMonth}</h2>
              <p className="app-reference">{view.selectedSource}</p>
              <AppResourceTable
                title={text.metrics}
                columns={[
                  { id: "metric", label: text.metric },
                  { id: "original", label: text.original, numeric: true },
                  { id: "proposed", label: text.proposed, numeric: true },
                  { id: "difference", label: text.difference, numeric: true },
                ]}
                rows={view.comparison}
              />
              <details className="app-amendment-disclosure">
                <summary>{text.viewOriginal}</summary>
                <div className="app-amendment-evidence">
                  <PayrollCalculationOutputSection view={view.original} locale={locale} />
                </div>
              </details>
              <details className="app-amendment-disclosure">
                <summary>{text.viewProposed}</summary>
                <div className="app-amendment-evidence">
                  <PayrollCalculationOutputSection view={view.proposed} locale={locale} />
                </div>
              </details>
            </section>
          )}
        </>
      )}
    </>
  ) : (
    <p role="status">{text.noCalculation}</p>
  );
}
