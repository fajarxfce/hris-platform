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
import type { useCalendarFilters } from "../controllers/use-calendar-filters";
import type { useCalendarReview } from "../controllers/use-calendar-review";
import { calendarMessages } from "../i18n/calendar-messages";
import type { calendarView } from "../models/calendar-view";

export function EmployeeCalendarPage(props: {
  state: ReturnType<typeof useCalendarReview>;
  filters: ReturnType<typeof useCalendarFilters>;
  view: ReturnType<typeof calendarView> | null;
  locale: Locale;
  companyName: string;
  backTo: string;
  weeklyTo: string | null;
  onRoster: ((date: string) => void) | null;
}) {
  const { state, view, filters, locale } = props;
  const text = calendarMessages(locale);
  return (
    <section aria-busy={state.loading}>
      <AppPageHeader
        title={state.review?.employee.legalName ?? text.calendar}
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
              label={text.from}
              type="date"
              min="0001-01-01"
              max="9999-12-31"
              {...filters.from}
            />
            <AppTextField
              label={text.until}
              type="date"
              min="0001-01-01"
              max="9999-12-31"
              {...filters.until}
            />
            <AppButton type="submit" appearance="primary">
              {text.apply}
            </AppButton>
          </div>
          <Text>{text.rangeHint}</Text>
          {filters.error && <p role="alert">{filters.error}</p>}
        </AppCommandBar>
      </form>
      <AppFailure failure={state.failure} locale={locale} />
      {state.loading && <AppLoading label={messages(locale).loading} />}
      {view && (
        <div className="app-report-content">
          <AppResourceTable
            title={text.calendar}
            columns={[
              { id: "date", label: text.date },
              { id: "status", label: text.status },
              { id: "shift", label: text.shift },
              { id: "hours", label: text.hours },
              { id: "zone", label: text.timezone },
              { id: "minutes", label: text.minutes, numeric: true },
              { id: "origin", label: text.origin },
            ]}
            rows={view.days}
            {...(props.onRoster ? { action: { label: text.roster, onOpen: props.onRoster } } : {})}
          />
          <div className="app-form-actions">
            {props.weeklyTo && <Link to={props.weeklyTo}>{text.weekly}</Link>}
            {view.effectiveFrom && (
              <Text>
                {text.effectiveFrom}: {view.effectiveFrom}
              </Text>
            )}
          </div>
          {view.pattern.length === 0 ? (
            <Text>{text.noPattern}</Text>
          ) : (
            <AppResourceTable
              title={text.pattern}
              columns={[
                { id: "day", label: text.date },
                { id: "shift", label: text.shift },
                { id: "hours", label: text.hours },
                { id: "zone", label: text.timezone },
                { id: "revision", label: text.revision },
              ]}
              rows={view.pattern}
            />
          )}
        </div>
      )}
    </section>
  );
}
