import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useWorkforceEmployeeFilters } from "../controllers/use-workforce-employee-filters";
import type { useWorkforceEmployees } from "../controllers/use-workforce-employees";
import { calendarMessages } from "../i18n/calendar-messages";
import type { workforceEmployeeView } from "../models/calendar-view";

export function WorkforceCalendarsPage(props: {
  state: ReturnType<typeof useWorkforceEmployees>;
  filters: ReturnType<typeof useWorkforceEmployeeFilters>;
  rows: ReturnType<typeof workforceEmployeeView>;
  companyName: string;
  locale: Locale;
  firstPage: boolean;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const { state, filters, locale } = props;
  const text = calendarMessages(locale);
  return (
    <section aria-busy={state.loading}>
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={
          <AppButton
            icon={<ArrowClockwise20Regular />}
            disabled={state.loading}
            onClick={state.refresh}
          >
            {messages(locale).refresh}
          </AppButton>
        }
      />
      <form onSubmit={filters.apply} noValidate>
        <AppCommandBar label={text.filters}>
          <div className="app-form-actions">
            <AppTextField label={text.query} maxLength={120} {...filters.query} />
            <AppTextField
              label={text.asOf}
              type="date"
              min="0001-01-01"
              max="9999-12-31"
              {...filters.asOf}
            />
            <AppButton type="submit" appearance="primary">
              {text.apply}
            </AppButton>
          </div>
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
                { id: "number", label: text.number },
                { id: "name", label: text.name },
                { id: "status", label: text.status },
              ]}
              rows={props.rows}
              action={{ label: text.view, onOpen: props.onOpen }}
            />
          ))}
        <fieldset className="app-pagination" aria-label={text.page}>
          <AppButton disabled={props.firstPage || state.loading} onClick={props.onFirst}>
            {text.first}
          </AppButton>
          <AppButton
            disabled={state.page?.nextCursor == null || state.loading}
            onClick={props.onNext}
          >
            {text.next}
          </AppButton>
        </fieldset>
      </div>
    </section>
  );
}
