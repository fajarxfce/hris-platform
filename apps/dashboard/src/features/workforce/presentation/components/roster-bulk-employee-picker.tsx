import { ArrowClockwise20Regular, Search20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppSelectableResourceTable } from "../../../../core/presentation/components/app-selectable-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useRosterBulkEmployees } from "../controllers/use-roster-bulk-employees";
import { calendarMessages } from "../i18n/calendar-messages";
import { rosterBulkMessages } from "../i18n/roster-bulk-messages";

export function RosterBulkEmployeePicker(props: {
  state: ReturnType<typeof useRosterBulkEmployees>;
  locale: Locale;
  rows: readonly Readonly<{
    id: string;
    label: string;
    cells: readonly string[];
    selected: boolean;
  }>[];
  pageChecked: boolean | "mixed";
  onToggle: (id: string, selected: boolean) => void;
  onTogglePage: (selected: boolean) => void;
}) {
  const { state, locale } = props,
    text = rosterBulkMessages(locale),
    calendar = calendarMessages(locale);
  return (
    <section className="app-report-content" aria-busy={state.loading}>
      <form className="app-resource-filters" onSubmit={state.search}>
        <AppTextField
          label={text.query}
          maxLength={120}
          contentBefore={<Search20Regular />}
          {...state.query}
        />
        <AppButton type="submit" appearance="subtle">
          {text.search}
        </AppButton>
        <AppButton
          appearance="subtle"
          icon={<ArrowClockwise20Regular />}
          disabled={state.loading}
          onClick={state.refresh}
        >
          {text.reload}
        </AppButton>
      </form>
      <AppFailure failure={state.failure} locale={locale} />
      {state.page && !state.page.bulkRosterSupported && <p role="alert">{text.unavailable}</p>}
      {state.loading && <AppLoading label={messages(locale).loading} />}
      {state.page?.bulkRosterSupported &&
        (props.rows.length ? (
          <AppSelectableResourceTable
            title={text.employees}
            columns={[
              { id: "number", label: calendar.number },
              { id: "name", label: calendar.name },
              { id: "status", label: calendar.status },
            ]}
            rows={props.rows}
            selectLabel={text.select}
            selectPageLabel={text.selectPage}
            pageChecked={props.pageChecked}
            onToggle={props.onToggle}
            onTogglePage={props.onTogglePage}
          />
        ) : (
          <p role="status">{text.empty}</p>
        ))}
      <fieldset className="app-pagination" aria-label={text.pages}>
        <AppButton disabled={state.firstPage || state.loading} onClick={state.first}>
          {text.first}
        </AppButton>
        <AppButton disabled={!state.page?.nextCursor || state.loading} onClick={state.next}>
          {text.next}
        </AppButton>
      </fieldset>
    </section>
  );
}
