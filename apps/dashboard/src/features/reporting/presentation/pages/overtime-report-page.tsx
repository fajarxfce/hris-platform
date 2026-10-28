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
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { overtimeStatuses } from "../../../workforce/domain/entities/overtime-request";
import type { useOvertimeReportFilters } from "../controllers/use-overtime-report-filters";
import { reportingMessages } from "../i18n/reporting-messages";
import { savedReportFilterMessages } from "../i18n/saved-report-filter-messages";
import type { OvertimeReportState } from "../models/overtime-report-state";
import type { overtimeReportView } from "../models/overtime-report-view";

export function OvertimeReportPage({
  state,
  view,
  filters,
  companyName,
  locale,
  onRefresh,
  savedFilterTo = null,
  onOpenMonth,
}: {
  state: OvertimeReportState;
  view: ReturnType<typeof overtimeReportView> | null;
  filters: ReturnType<typeof useOvertimeReportFilters>;
  companyName: string;
  locale: Locale;
  onRefresh: () => void;
  savedFilterTo?: string | null;
  onOpenMonth: (month: string) => void;
}) {
  const text = reportingMessages(locale);
  const shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.overtimeTitle}
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
            min="2000-01-01"
            max="2100-12-31"
            error={filters.error}
          />
          <AppTextField
            label={text.until}
            type="date"
            {...filters.until}
            min="2000-01-01"
            max="2100-12-31"
          />
          <AppSelect label={text.overtimeStatus} {...filters.status} error={filters.statusError}>
            <option value="">{text.allStatuses}</option>
            {overtimeStatuses.map((status) => (
              <option key={status} value={status}>
                {text[status]}
              </option>
            ))}
          </AppSelect>
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
          <Text className="app-muted">{text.overtimeDefinition}</Text>
          <AppResourceTable
            title={text.byMonth}
            columns={[
              { id: "month", label: text.month },
              ...(
                [
                  "requests",
                  "employments",
                  "actualRequests",
                  "requestedMinutes",
                  "actualMinutes",
                  "approvedMinutes",
                ] as const
              ).map((key) => ({ id: key, label: text[key], numeric: true })),
            ]}
            rows={view.rows}
            action={{ label: text.viewRequests, onOpen: onOpenMonth, columnId: "month" }}
          />
        </div>
      )}
    </section>
  );
}
