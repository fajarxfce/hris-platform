import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useAttendanceExceptionFilters } from "../controllers/use-attendance-exception-filters";
import type { useAttendanceExceptions } from "../controllers/use-attendance-exceptions";
import { attendanceExceptionMessages } from "../i18n/attendance-exception-messages";
import { attendanceMessages } from "../i18n/attendance-messages";
import { calendarMessages } from "../i18n/calendar-messages";
import type { attendanceExceptionRows } from "../models/attendance-exception-view";

export function AttendanceExceptionsPage(props: {
  state: ReturnType<typeof useAttendanceExceptions>;
  filters: ReturnType<typeof useAttendanceExceptionFilters>;
  rows: ReturnType<typeof attendanceExceptionRows>;
  locale: Locale;
  companyName: string;
  backTo: string;
  firstPage: boolean;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const { state, filters, locale } = props;
  const text = attendanceExceptionMessages(locale),
    attendance = attendanceMessages(locale),
    calendar = calendarMessages(locale);
  return (
    <section aria-busy={state.loading}>
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={
          <>
            <Link to={props.backTo}>{attendance.back}</Link>
            <AppButton
              icon={<ArrowClockwise20Regular />}
              appearance="subtle"
              disabled={state.loading}
              onClick={state.refresh}
            >
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
            <AppSelect label={text.kind} {...filters.kind}>
              <option value="">{text.all}</option>
              <option value="PENDING">{text.pending}</option>
              <option value="INCOMPLETE">{text.incomplete}</option>
            </AppSelect>
            <AppButton type="submit" appearance="primary">
              {calendar.apply}
            </AppButton>
          </div>
          <Text>{attendance.rangeHint}</Text>
          {filters.error && <p role="alert">{filters.error}</p>}
        </AppCommandBar>
      </form>
      <AppFailure failure={state.failure} locale={locale} />
      {state.loading && <AppLoading label={messages(locale).loading} />}
      <div className="app-report-content">
        {state.page &&
          (props.rows.length === 0 ? (
            <Text role="status">{text.empty}</Text>
          ) : (
            <AppResourceTable
              title={text.directory}
              columns={[
                { id: "date", label: attendance.date },
                { id: "name", label: calendar.name },
                { id: "pending", label: text.pending, numeric: true },
                { id: "pair", label: text.pair },
                { id: "received", label: text.received },
              ]}
              rows={props.rows}
              action={{ label: attendance.day, onOpen: props.onOpen }}
            />
          ))}
        <fieldset className="app-pagination" aria-label={text.pages}>
          <AppButton
            appearance="subtle"
            disabled={props.firstPage || state.loading}
            onClick={props.onFirst}
          >
            {calendar.first}
          </AppButton>
          <AppButton
            appearance="subtle"
            disabled={state.page?.nextCursor == null || state.loading}
            onClick={props.onNext}
          >
            {calendar.next}
          </AppButton>
        </fieldset>
      </div>
    </section>
  );
}
