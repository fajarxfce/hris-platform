import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppMetricCard } from "../../../../core/presentation/components/app-metric-card";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useLeaveReportFilters } from "../controllers/use-leave-report-filters";
import { reportingMessages } from "../i18n/reporting-messages";
import { savedReportFilterMessages } from "../i18n/saved-report-filter-messages";
import type { LeaveReportState } from "../models/leave-report-state";
import type { leaveReportView } from "../models/leave-report-view";

export function LeaveReportPage({
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
  state: LeaveReportState;
  view: ReturnType<typeof leaveReportView> | null;
  filters: ReturnType<typeof useLeaveReportFilters>;
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
        title={text.leaveTitle}
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
              <Link to={savedFilterTo}>{savedReportFilterMessages(locale).saveApplied}</Link>
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
          <Text className="app-muted">{text.leaveDefinition}</Text>
          <AppButton onClick={onOpenAll} disabled={state.report?.totals.requests === 0}>
            {text.allRequests}
          </AppButton>
          <AppResourceTable
            title={text.leaveByStatus}
            columns={[
              { id: "status", label: text.leaveStatus },
              { id: "requests", label: text.leaveRequests, numeric: true },
              { id: "employments", label: text.employments, numeric: true },
              { id: "days", label: text.requestedDays, numeric: true },
            ]}
            rows={view.rows}
            action={{ label: text.viewRequests, onOpen: onOpenStatus, columnId: "status" }}
          />
        </div>
      )}
    </section>
  );
}
