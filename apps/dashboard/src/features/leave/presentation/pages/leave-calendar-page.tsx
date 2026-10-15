import {
  ArrowClockwise20Regular,
  ChevronLeft20Regular,
  ChevronRight20Regular,
} from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppMonthCalendar } from "../../../../core/presentation/components/app-month-calendar";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { LeaveCalendarScope } from "../../domain/entities/leave-calendar";
import type { useLeaveCalendarMonth } from "../controllers/use-leave-calendar-month";
import { leaveCalendarMessages } from "../i18n/leave-calendar-messages";
import { leaveMessages } from "../i18n/leave-messages";
import type { LeaveCalendarState } from "../models/leave-calendar-state";
import type { leaveCalendarRows, leaveCalendarView } from "../models/leave-calendar-view";

export function LeaveCalendarPage(props: {
  state: LeaveCalendarState;
  view: ReturnType<typeof leaveCalendarView> | null;
  rows: ReturnType<typeof leaveCalendarRows>;
  scope: LeaveCalendarScope | null;
  scopes: readonly Readonly<{ value: LeaveCalendarScope; label: string }>[];
  form: ReturnType<typeof useLeaveCalendarMonth>;
  companyName: string;
  locale: Locale;
  onRefresh: () => void;
  onScope: (scope: string) => void;
  onPrevious: () => void;
  onNext: () => void;
  onToday: () => void;
  onDate: (date: string) => void;
  onFirstPage: () => void;
  onNextPage: () => void;
  onOpen: (id: string) => void;
}) {
  const { state, view, locale } = props,
    text = leaveCalendarMessages(locale),
    shared = messages(locale),
    leave = leaveMessages(locale);
  return (
    <section className="app-report-content" aria-busy={state.stage === "loading"}>
      <AppPageHeader title={text.title} context={props.companyName} />
      <AppCommandBar label={text.title}>
        <AppButton
          appearance="subtle"
          icon={<ChevronLeft20Regular />}
          aria-label={text.previous}
          disabled={!view?.previousMonth}
          onClick={props.onPrevious}
        />
        <AppButton
          appearance="subtle"
          icon={<ChevronRight20Regular />}
          aria-label={text.next}
          disabled={!view?.nextMonth}
          onClick={props.onNext}
        />
        <AppButton appearance="subtle" disabled={state.stage === "loading"} onClick={props.onToday}>
          {text.today}
        </AppButton>
        <AppButton
          appearance="subtle"
          icon={<ArrowClockwise20Regular />}
          disabled={state.stage === "loading"}
          onClick={props.onRefresh}
        >
          {shared.refresh}
        </AppButton>
      </AppCommandBar>
      <div className="app-resource-filters">
        <AppSelect
          label={text.scope}
          value={props.scope ?? ""}
          onChange={(event) => props.onScope(event.target.value)}
        >
          <option value="" disabled>
            {text.chooseScope}
          </option>
          {props.scopes.map((scope) => (
            <option key={scope.value} value={scope.value}>
              {scope.label}
            </option>
          ))}
        </AppSelect>
        <form className="app-leave-calendar-month" onSubmit={props.form.apply}>
          <AppTextField
            {...props.form.month}
            label={text.month}
            type="month"
            min="1900-01"
            max="2200-12"
            error={props.form.error}
          />
          <AppButton appearance="subtle" type="submit" disabled={state.stage === "loading"}>
            {text.changeMonth}
          </AppButton>
        </form>
      </div>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {view && state.calendar && (
        <div className="app-leave-calendar-layout">
          <section className="app-leave-calendar-month-panel" aria-label={view.title}>
            <h2>{view.title}</h2>
            <p className="app-muted">{text.calendarHint}</p>
            <AppMonthCalendar
              title={view.title}
              weeks={view.weeks}
              weekdays={view.weekdays}
              onSelect={props.onDate}
            />
            <p className="app-muted">
              {text.timezone}: {state.calendar.timezone}
            </p>
          </section>
          <section
            className="app-leave-calendar-day-panel"
            aria-labelledby="leave-calendar-selected-date"
            aria-busy={state.entriesStage === "loading"}
          >
            <header>
              <h2 id="leave-calendar-selected-date">{view.selectedTitle}</h2>
              <p className="app-muted">{view.counts}</p>
            </header>
            <AppFailure failure={state.entriesFailure} locale={locale} />
            {state.entriesStage === "loading" && <AppLoading label={shared.loading} />}
            {state.entries &&
              (props.rows.length > 0 ? (
                <AppResourceTable
                  title={text.dateDetails}
                  columns={[
                    { id: "employee", label: leave.employee },
                    { id: "number", label: leave.number },
                    { id: "type", label: leave.type },
                    { id: "portion", label: leave.portion },
                    { id: "status", label: leave.status },
                  ]}
                  rows={props.rows}
                  minWidth={650}
                  action={{ label: leave.view, columnId: "employee", onOpen: props.onOpen }}
                />
              ) : (
                <p role="status">{text.empty}</p>
              ))}
            <fieldset className="app-pagination" aria-label={leave.pages}>
              <AppButton
                appearance="subtle"
                disabled={state.after === null || state.entriesStage === "loading"}
                onClick={props.onFirstPage}
              >
                {leave.first}
              </AppButton>
              <AppButton
                appearance="subtle"
                disabled={!state.entries?.entries.nextCursor || state.entriesStage === "loading"}
                onClick={props.onNextPage}
              >
                {leave.next}
              </AppButton>
            </fieldset>
            <p className="app-muted">{text.dateRule}</p>
          </section>
        </div>
      )}
    </section>
  );
}
