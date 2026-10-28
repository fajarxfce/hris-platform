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
import type { useAttendanceReportFilters } from "../controllers/use-attendance-report-filters";
import { reportingMessages } from "../i18n/reporting-messages";
import type { AttendanceReportState } from "../models/attendance-report-state";
import type { attendanceReportView } from "../models/attendance-report-view";

export function AttendanceReportPage({
  state,
  view,
  filters,
  companyName,
  locale,
  onRefresh,
  savedFilterTo = null,
  onOpenDay,
}: {
  state: AttendanceReportState;
  view: ReturnType<typeof attendanceReportView> | null;
  filters: ReturnType<typeof useAttendanceReportFilters>;
  companyName: string;
  locale: Locale;
  onRefresh: () => void;
  savedFilterTo?: string | null;
  onOpenDay: (date: string) => void;
}) {
  const text = reportingMessages(locale);
  const shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.attendanceTitle}
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
            max="2100-12-31"
            error={filters.error}
          />
          <AppTextField
            label={text.until}
            type="date"
            {...filters.until}
            min="1900-01-01"
            max="2100-12-31"
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
          <Text className="app-muted">{text.attendanceDefinition}</Text>
          <AppResourceTable
            title={text.byDate}
            columns={[
              { id: "date", label: text.workDate },
              { id: "affected", label: text.affectedDays, numeric: true },
              { id: "pending", label: text.pendingDays, numeric: true },
              { id: "incomplete", label: text.incompleteDays, numeric: true },
            ]}
            rows={view.rows}
            action={{ label: text.viewExceptions, onOpen: onOpenDay, columnId: "date" }}
          />
        </div>
      )}
    </section>
  );
}
