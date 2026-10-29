import { Checkbox } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { PayrollComparisonAmounts } from "../../domain/entities/payroll-run-comparison";
import { payrollComparisonMessages } from "../i18n/payroll-comparison-messages";
import { payrollRunEmployeePath, payrollRunPath } from "../models/payroll-calculation-route";
import type { PayrollComparisonState } from "../models/payroll-comparison-state";
import { payrollAmountView } from "../models/payroll-policy-view";
export function PayrollComparisonPage(props: {
  state: PayrollComparisonState;
  companyId: string;
  companyName: string;
  locale: Locale;
  backTo: string;
  month: string;
  periodFirst: boolean;
  employeeFirst: boolean;
  changedOnly: boolean;
  baselineSelected: boolean;
  onMonth: (value: string) => void;
  onSearch: () => void;
  onChoose: (id: string) => void;
  onPeriodFirst: () => void;
  onPeriodNext: () => void;
  onEmployeeFirst: () => void;
  onEmployeeNext: () => void;
  onFilter: (value: boolean) => void;
  onRefresh: () => void;
  onSelect: (id: string) => void;
  onDismiss: () => void;
}) {
  const text = payrollComparisonMessages(props.locale),
    shared = messages(props.locale),
    { context, comparison, selected } = props.state;
  const pending = props.state.stage === "loading";
  const amount = (v: string) => payrollAmountView(v, props.locale);
  const keys = ["taxableGross", "withheld", "takeHome"] as const;
  const moneyRows = (
    baseline: PayrollComparisonAmounts | null,
    current: PayrollComparisonAmounts | null,
    delta: PayrollComparisonAmounts,
  ) =>
    keys.map((key) => ({
      id: key,
      cells: [
        text[key],
        baseline ? amount(baseline[key]) : "—",
        current ? amount(current[key]) : "—",
        amount(delta[key]),
      ],
    }));
  const moneyColumns = [
    { id: "measure", label: text.totals },
    { id: "baseline", label: text.baseline, numeric: true },
    { id: "current", label: text.current, numeric: true },
    { id: "delta", label: text.delta, numeric: true },
  ];
  const chosen = comparison?.employees.items.find((row) => row.employmentId === selected);
  return (
    <section aria-busy={pending}>
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={
          <>
            <Link to={props.backTo}>{text.back}</Link>
            <AppButton disabled={pending} onClick={props.onRefresh}>
              {shared.refresh}
            </AppButton>
          </>
        }
      />
      <div className="app-report-content">
        <AppFailure failure={props.state.failure} locale={props.locale} />
        {pending && <AppLoading label={shared.loading} />}
        <p>{text.notice}</p>
        {context && (
          <>
            <AppPropertyList
              title={text.current}
              items={[
                { label: text.month, value: context.run.earningsMonth },
                {
                  label: text.status,
                  value: text.statuses[context.run.status as "CALCULATED" | "FINALIZED"],
                },
              ]}
            />
            <section className="app-resource-panel" aria-label={text.search}>
              <h2>{text.search}</h2>
              <p>{text.ready}</p>
              <form
                className="app-filter-bar"
                onSubmit={(event) => {
                  event.preventDefault();
                  props.onSearch();
                }}
              >
                <AppTextField
                  type="month"
                  label={text.baselineMonth}
                  value={props.month}
                  onChange={(_event, data) => props.onMonth(data.value)}
                  min="2024-01"
                  required
                />
                <AppButton type="submit" disabled={pending}>
                  {text.search}
                </AppButton>
              </form>
            </section>
            <AppResourceTable
              title={text.periods}
              columns={[
                { id: "month", label: text.month },
                { id: "date", label: text.paymentDate },
                { id: "status", label: text.status },
              ]}
              rows={context.periods.items.map((period) => ({
                id: period.id,
                actionLabel: `${period.earningsMonth} · ${period.plannedPaymentDate}`,
                actionDisabled:
                  period.currentRunId === null ||
                  !["CALCULATED", "FINALIZED"].includes(period.status),
                cells: [
                  period.earningsMonth,
                  period.plannedPaymentDate,
                  text.statuses[period.status],
                ],
              }))}
              action={{ label: text.choose, onOpen: props.onChoose }}
            />
            {context.periods.items.length === 0 && <p role="status">{text.empty}</p>}
            <div className="app-pagination">
              <AppButton disabled={props.periodFirst || pending} onClick={props.onPeriodFirst}>
                {text.firstPeriods}
              </AppButton>
              <AppButton
                disabled={!context.periods.nextCursor || pending}
                onClick={props.onPeriodNext}
              >
                {text.nextPeriods}
              </AppButton>
            </div>
          </>
        )}
        {props.baselineSelected && !props.state.failure && (
          <Checkbox
            label={text.changesOnly}
            checked={props.changedOnly}
            disabled={pending}
            onChange={(_event, data) => props.onFilter(data.checked === true)}
          />
        )}
        {comparison && (
          <>
            <AppPropertyList
              title={text.comparedRuns}
              items={[
                {
                  label: text.baseline,
                  value: (
                    <Link to={payrollRunPath(props.companyId, comparison.baseline.id)}>
                      {comparison.baseline.earningsMonth} · #{comparison.baseline.number}
                    </Link>
                  ),
                },
                {
                  label: text.current,
                  value: (
                    <Link to={payrollRunPath(props.companyId, comparison.current.id)}>
                      {comparison.current.earningsMonth} · #{comparison.current.number}
                    </Link>
                  ),
                },
              ]}
            />
            <AppResourceTable
              title={text.totals}
              columns={moneyColumns}
              rows={moneyRows(
                comparison.summary.baseline,
                comparison.summary.current,
                comparison.summary.delta,
              )}
            />
            <AppPropertyList
              title={text.counts}
              items={(
                [
                  "baselineEmployees",
                  "currentEmployees",
                  "matched",
                  "added",
                  "removed",
                  "changed",
                  "unchanged",
                ] as const
              ).map((key) => ({
                label: text[key],
                value: new Intl.NumberFormat(props.locale).format(comparison.summary[key]),
              }))}
            />
            <AppResourceTable
              title={text.employees}
              columns={[
                { id: "employee", label: text.employee },
                { id: "change", label: text.status },
                { id: "delta", label: `${text.delta} · ${text.takeHome}`, numeric: true },
              ]}
              rows={comparison.employees.items.map((row) => ({
                id: row.employmentId,
                actionLabel: row.current?.name ?? row.baseline?.name ?? "",
                cells: [
                  `${row.current?.number ?? row.baseline?.number} · ${row.current?.name ?? row.baseline?.name}`,
                  text.changes[row.change],
                  amount(row.delta.takeHome),
                ],
              }))}
              action={{ label: text.open, onOpen: props.onSelect }}
            />
            {comparison.employees.items.length === 0 && <p role="status">{text.noEmployees}</p>}
            <div className="app-pagination">
              <AppButton disabled={props.employeeFirst || pending} onClick={props.onEmployeeFirst}>
                {text.firstEmployees}
              </AppButton>
              <AppButton
                disabled={!comparison.employees.nextCursor || pending}
                onClick={props.onEmployeeNext}
              >
                {text.nextEmployees}
              </AppButton>
            </div>
          </>
        )}
      </div>
      {chosen && comparison && (
        <AppDetailsPanel
          open
          title={text.detail}
          closeLabel={shared.close}
          onClose={props.onDismiss}
          width={820}
        >
          <AppPropertyList
            title={text.employee}
            items={[
              {
                label: text.baseline,
                value: chosen.baseline
                  ? `${chosen.baseline.number} · ${chosen.baseline.name}`
                  : "—",
              },
              {
                label: text.current,
                value: chosen.current ? `${chosen.current.number} · ${chosen.current.name}` : "—",
              },
              { label: text.status, value: text.changes[chosen.change] },
            ]}
          />
          <AppResourceTable
            title={text.detail}
            columns={moneyColumns}
            rows={moneyRows(
              chosen.baseline?.amounts ?? null,
              chosen.current?.amounts ?? null,
              chosen.delta,
            )}
          />
          {chosen.baseline && (
            <p>
              <Link
                to={payrollRunEmployeePath(
                  props.companyId,
                  comparison.baseline.id,
                  chosen.employmentId,
                )}
              >
                {text.originalResult}
              </Link>
            </p>
          )}
          {chosen.current && (
            <p>
              <Link
                to={payrollRunEmployeePath(
                  props.companyId,
                  comparison.current.id,
                  chosen.employmentId,
                )}
              >
                {text.currentResult}
              </Link>
            </p>
          )}
        </AppDetailsPanel>
      )}
    </section>
  );
}
