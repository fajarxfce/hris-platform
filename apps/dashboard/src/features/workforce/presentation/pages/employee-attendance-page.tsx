import { Text } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useAttendanceFilters } from "../controllers/use-attendance-filters";
import type { useEmployeeAttendance } from "../controllers/use-employee-attendance";
import { attendanceMessages } from "../i18n/attendance-messages";
import { calendarMessages } from "../i18n/calendar-messages";
import type { attendanceOverviewView } from "../models/attendance-view";

export function EmployeeAttendancePage(props: {
  state: ReturnType<typeof useEmployeeAttendance>;
  filters: ReturnType<typeof useAttendanceFilters>;
  rows: ReturnType<typeof attendanceOverviewView>;
  locale: Locale;
  companyName: string;
  backTo: string;
  onDay: (date: string) => void;
}) {
  const { state, filters, locale } = props,
    text = attendanceMessages(locale),
    calendar = calendarMessages(locale);
  return (
    <section aria-busy={state.loading}>
      <AppPageHeader
        title={state.attendance?.employee.legalName ?? text.title}
        context={`${props.companyName} / ${text.title}`}
        actions={
          <>
            <Link to={props.backTo}>{text.back}</Link>
            <AppButton onClick={state.refresh} disabled={state.loading}>
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <form onSubmit={filters.apply} noValidate>
        <AppCommandBar label={text.filters}>
          <div className="app-form-actions">
            <AppTextField
              label={calendar.from}
              type="date"
              min="0001-01-01"
              max="9999-12-31"
              {...filters.from}
            />
            <AppTextField
              label={calendar.until}
              type="date"
              min="0001-01-01"
              max="9999-12-31"
              {...filters.until}
            />
            <AppButton type="submit" appearance="primary">
              {calendar.apply}
            </AppButton>
          </div>
          <Text>{text.rangeHint}</Text>
          {filters.error && <p role="alert">{filters.error}</p>}
        </AppCommandBar>
      </form>
      <AppFailure failure={state.failure} locale={locale} />
      {state.loading && <AppLoading label={messages(locale).loading} />}
      {state.attendance && (
        <div className="app-report-content">
          <AppResourceTable
            title={text.title}
            columns={[
              { id: "date", label: text.date },
              { id: "minutes", label: text.accepted, numeric: true },
              { id: "pending", label: text.pending, numeric: true },
              { id: "status", label: text.status },
            ]}
            rows={props.rows}
            action={{ label: text.day, onOpen: props.onDay }}
          />
        </div>
      )}
    </section>
  );
}
