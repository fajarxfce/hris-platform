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
import type { useAttendanceClosingReportFilters } from "../controllers/use-attendance-closing-report-filters";
import { attendanceClosingReportMessages } from "../i18n/attendance-closing-report-messages";
import { reportingMessages } from "../i18n/reporting-messages";
import type { AttendanceClosingReportState } from "../models/attendance-closing-report-state";
import type { attendanceClosingReportView } from "../models/attendance-closing-report-view";

export function AttendanceClosingReportPage({
  state,
  view,
  filters,
  companyName,
  locale,
  onRefresh,
  onOpenMonth,
}: {
  state: AttendanceClosingReportState;
  view: ReturnType<typeof attendanceClosingReportView> | null;
  filters: ReturnType<typeof useAttendanceClosingReportFilters>;
  companyName: string;
  locale: Locale;
  onRefresh: () => void;
  onOpenMonth: (month: string) => void;
}) {
  const closing = attendanceClosingReportMessages(locale);
  const text = reportingMessages(locale),
    shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={closing.title}
        context={`${companyName} / ${shared.reports}`}
        actions={
          <AppButton
            icon={<ArrowClockwise20Regular />}
            onClick={onRefresh}
            disabled={state.stage === "loading"}
          >
            {shared.refresh}
          </AppButton>
        }
      />
      <form onSubmit={filters.apply} noValidate>
        <AppCommandBar label={text.filters}>
          <AppTextField
            label={text.fromMonth}
            type="month"
            {...filters.from}
            min="2000-01"
            max="2100-12"
            error={filters.error}
          />
          <AppTextField
            label={text.untilMonth}
            type="month"
            {...filters.until}
            min="2000-01"
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
          <Text className="app-muted">{closing.definition}</Text>
          <AppResourceTable
            title={closing.compare}
            minWidth={0}
            columns={[
              { id: "measure", label: closing.metric },
              { id: "original", label: closing.original, numeric: true },
              { id: "current", label: closing.current, numeric: true },
            ]}
            rows={view.comparisons}
          />
          <AppResourceTable
            title={closing.byMonth}
            columns={[
              { id: "month", label: closing.month },
              { id: "status", label: closing.status },
              ...(
                [
                  "employees",
                  "adjusted",
                  "originalMinutes",
                  "currentMinutes",
                  "originalAbsence",
                  "currentAbsence",
                ] as const
              ).map((id) => ({ id, label: closing[id], numeric: true })),
            ]}
            rows={view.rows}
            action={{ label: closing.viewEmployees, onOpen: onOpenMonth, columnId: "month" }}
          />
        </div>
      )}
    </section>
  );
}
