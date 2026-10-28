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
import type { useExpenseReportFilters } from "../controllers/use-expense-report-filters";
import { reportingMessages } from "../i18n/reporting-messages";
import type { ExpenseReportState } from "../models/expense-report-state";
import type { expenseReportView } from "../models/expense-report-view";

export function ExpenseReportPage({
  state,
  view,
  filters,
  companyName,
  locale,
  onRefresh,
  savedFilterTo = null,
  onOpenStatus,
  onOpenAll,
}: {
  state: ExpenseReportState;
  view: ReturnType<typeof expenseReportView> | null;
  filters: ReturnType<typeof useExpenseReportFilters>;
  companyName: string;
  locale: Locale;
  onRefresh: () => void;
  savedFilterTo?: string | null;
  onOpenStatus: (status: string) => void;
  onOpenAll: () => void;
}) {
  const text = reportingMessages(locale);
  const shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.expenseTitle}
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
            label={text.from}
            type="date"
            {...filters.from}
            min="1900-01-01"
            max="2200-12-31"
            error={filters.error}
          />
          <AppTextField
            label={text.until}
            type="date"
            {...filters.until}
            min="1900-01-01"
            max="2200-12-31"
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
          <Text className="app-muted">{text.expenseDefinition}</Text>
          <AppButton onClick={onOpenAll} disabled={state.report?.totals.claims === 0}>
            {text.allClaims}
          </AppButton>
          <AppResourceTable
            title={text.expenseByStatus}
            columns={[
              { id: "status", label: text.expenseStatus },
              { id: "claims", label: text.expenseClaims, numeric: true },
              { id: "employments", label: text.employments, numeric: true },
              { id: "lines", label: text.expenseLines, numeric: true },
              { id: "amount", label: text.periodAmount, numeric: true },
            ]}
            rows={view.rows}
            action={{ label: text.viewClaims, onOpen: onOpenStatus, columnId: "status" }}
          />
        </div>
      )}
    </section>
  );
}
