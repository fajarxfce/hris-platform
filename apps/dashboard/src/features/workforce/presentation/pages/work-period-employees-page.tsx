import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular, Search20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useWorkPeriodEmployees } from "../controllers/use-work-period-employees";
import { workPeriodMessages } from "../i18n/work-period-messages";
import type { workforceEmployeeView } from "../models/calendar-view";

export function WorkPeriodEmployeesPage(props: {
  state: ReturnType<typeof useWorkPeriodEmployees>;
  rows: ReturnType<typeof workforceEmployeeView>;
  locale: Locale;
  onOpen: (id: string) => void;
}) {
  const { state, locale } = props,
    text = workPeriodMessages(locale);
  return (
    <section className="app-report-content" aria-label={text.employees} aria-busy={state.loading}>
      <form className="app-resource-filters" onSubmit={state.apply} noValidate>
        <AppTextField
          label={text.search}
          {...state.query}
          maxLength={120}
          type="search"
          contentBefore={<Search20Regular />}
        />
        <AppButton type="submit" disabled={state.loading}>
          {text.searchAction}
        </AppButton>
        <AppButton
          appearance="subtle"
          icon={<ArrowClockwise20Regular />}
          disabled={state.loading}
          onClick={state.refresh}
        >
          {messages(locale).refresh}
        </AppButton>
      </form>
      <AppFailure failure={state.failure} locale={locale} />
      {state.loading && <AppLoading label={messages(locale).loading} />}
      {state.page &&
        (props.rows.length === 0 ? (
          <Text role="status">{text.empty}</Text>
        ) : (
          <AppResourceTable
            title={text.employees}
            columns={[
              { id: "number", label: text.employeeNumber },
              { id: "name", label: text.employee },
              { id: "status", label: text.employmentStatus },
            ]}
            rows={props.rows}
            action={{ label: text.view, onOpen: props.onOpen }}
          />
        ))}
      <fieldset className="app-pagination" aria-label={text.pages}>
        <AppButton
          appearance="subtle"
          disabled={state.firstPage || state.loading}
          onClick={state.first}
        >
          {text.first}
        </AppButton>
        <AppButton
          appearance="subtle"
          disabled={!state.page?.nextCursor || state.loading}
          onClick={state.next}
        >
          {text.next}
        </AppButton>
      </fieldset>
    </section>
  );
}
