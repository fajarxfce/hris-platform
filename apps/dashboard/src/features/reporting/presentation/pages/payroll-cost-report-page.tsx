import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppMetricCard } from "../../../../core/presentation/components/app-metric-card";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { ReportSelectionActions } from "../components/report-selection-actions";
import type { usePayrollCostReportFilters } from "../controllers/use-payroll-cost-report-filters";
import { reportingMessages } from "../i18n/reporting-messages";
import type { PayrollCostReportState } from "../models/payroll-cost-report-state";
import type { payrollCostReportView } from "../models/payroll-cost-report-view";

export function PayrollCostReportPage({
  state,
  view,
  filters,
  companyName,
  locale,
  onRefresh,
  savedFilterTo = null,
  onOpenMonth,
}: {
  state: PayrollCostReportState;
  view: ReturnType<typeof payrollCostReportView> | null;
  filters: ReturnType<typeof usePayrollCostReportFilters>;
  companyName: string;
  locale: Locale;
  onRefresh: () => void;
  savedFilterTo?: string | null;
  onOpenMonth: (month: string) => void;
}) {
  const text = reportingMessages(locale),
    shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.payrollCostTitle}
        context={`${companyName} / ${shared.reports}`}
        actions={
          <>
            <AppButton
              icon={<ArrowClockwise20Regular />}
              onClick={onRefresh}
              disabled={state.stage === "loading"}
            >
              {shared.refresh}
            </AppButton>
            {savedFilterTo && (
              <ReportSelectionActions savedFilterTo={savedFilterTo} locale={locale} />
            )}
          </>
        }
      />
      <form onSubmit={filters.apply} noValidate>
        <AppCommandBar label={text.filters}>
          <AppTextField
            label={text.fromMonth}
            type="month"
            {...filters.from}
            min="2024-01"
            max="2100-12"
            error={filters.error}
          />
          <AppTextField
            label={text.untilMonth}
            type="month"
            {...filters.until}
            min="2024-01"
            max="2100-12"
          />
          <AppButton type="submit" appearance="primary">
            {text.apply}
          </AppButton>
        </AppCommandBar>
      </form>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {view && (
        <div className="app-report-content">
          <div className="app-metric-grid">
            {view.metrics.map((metric) => (
              <AppMetricCard key={metric.key} label={metric.label} value={metric.value} />
            ))}
          </div>
          <Text className="app-muted">{text.payrollCostDefinition}</Text>
          <AppResourceTable
            title={text.payrollByMonth}
            columns={[
              { id: "month", label: text.earningsMonth },
              ...view.metrics.map((metric) => ({
                id: metric.key,
                label: metric.label,
                numeric: true,
              })),
            ]}
            rows={view.rows}
            action={{ label: text.viewPayslips, onOpen: onOpenMonth, columnId: "month" }}
          />
        </div>
      )}
    </section>
  );
}
