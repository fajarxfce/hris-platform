import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular, Search20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useExpenseDraftEmployees } from "../controllers/use-expense-draft-employees";
import { expenseDraftMessages } from "../i18n/expense-draft-messages";
export function ExpenseDraftPickerPage(props: {
  state: Omit<ReturnType<typeof useExpenseDraftEmployees>, "directory">;
  title: string;
  searchLabel?: string;
  hint?: string;
  ready: boolean;
  hasNext: boolean;
  rows: readonly Readonly<{
    id: string;
    actionLabel?: string;
    actionDisabled?: boolean;
    cells: readonly string[];
  }>[];
  columns: readonly Readonly<{ id: string; label: string }>[];
  locale: Locale;
  onSelect: (id: string) => void;
}) {
  const { state, locale } = props,
    text = expenseDraftMessages(locale);
  return (
    <section className="app-report-content" aria-busy={state.loading} aria-label={props.title}>
      {props.hint && <p>{props.hint}</p>}
      <form className="app-form-actions" onSubmit={state.submit} noValidate>
        {props.searchLabel && (
          <>
            <AppTextField
              label={props.searchLabel}
              {...state.input}
              maxLength={120}
              type="search"
              contentBefore={<Search20Regular />}
            />
            <AppButton type="submit">{text.search}</AppButton>
          </>
        )}
        <AppButton
          appearance="subtle"
          icon={<ArrowClockwise20Regular />}
          onClick={state.refresh}
          disabled={state.loading}
        >
          {messages(locale).refresh}
        </AppButton>
      </form>
      <AppFailure failure={state.failure} locale={locale} />
      {state.loading && <AppLoading label={messages(locale).loading} />}
      {props.ready &&
        (props.rows.length === 0 ? (
          <Text role="status">{text.noResults}</Text>
        ) : (
          <AppResourceTable
            title={props.title}
            columns={props.columns}
            rows={props.rows}
            minWidth={400}
            action={{ label: text.select, onOpen: props.onSelect }}
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
          disabled={!props.hasNext || state.loading}
          onClick={state.next}
        >
          {text.next}
        </AppButton>
      </fieldset>
    </section>
  );
}
