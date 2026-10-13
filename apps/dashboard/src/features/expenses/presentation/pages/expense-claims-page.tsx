import { Text } from "@fluentui/react-components";
import { Add20Regular, ArrowClockwise20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { expenseClaimStatuses } from "../../domain/entities/expense-claim";
import type { useExpenseClaimFilters } from "../controllers/use-expense-claim-filters";
import type { useExpenseClaims } from "../controllers/use-expense-claims";
import { expenseClaimMessages } from "../i18n/expense-claim-messages";
import { expenseDraftMessages } from "../i18n/expense-draft-messages";
import type { expenseClaimRows } from "../models/expense-claim-view";
export function ExpenseClaimsPage(props: {
  state: ReturnType<typeof useExpenseClaims>;
  filters: ReturnType<typeof useExpenseClaimFilters>;
  rows: ReturnType<typeof expenseClaimRows>;
  companyName: string;
  locale: Locale;
  firstPage: boolean;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
  onCreate: (() => void) | null;
}) {
  const { state, filters, locale } = props,
    text = expenseClaimMessages(locale);
  return (
    <section aria-busy={state.loading}>
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={
          <>
            {props.onCreate && (
              <AppButton icon={<Add20Regular />} onClick={props.onCreate}>
                {expenseDraftMessages(locale).create}
              </AppButton>
            )}
            <AppButton
              appearance="subtle"
              icon={<ArrowClockwise20Regular />}
              onClick={state.refresh}
              disabled={state.loading}
            >
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <form onSubmit={filters.submit} noValidate>
        <AppCommandBar label={text.filters}>
          <div className="app-form-actions">
            <AppTextField
              label={text.from}
              type="date"
              min="1900-01-01"
              max="2200-12-31"
              {...filters.from}
            />
            <AppTextField
              label={text.until}
              type="date"
              min="1900-01-01"
              max="2200-12-31"
              {...filters.until}
            />
            <AppSelect label={text.status} {...filters.status}>
              <option value="">{text.all}</option>
              {expenseClaimStatuses.map((status) => (
                <option key={status} value={status}>
                  {text[status]}
                </option>
              ))}
            </AppSelect>
            <AppButton type="submit" appearance="primary">
              {text.apply}
            </AppButton>
          </div>
          <Text>{text.range}</Text>
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
              title={text.title}
              minWidth={900}
              columns={[
                { id: "title", label: text.claimTitle },
                { id: "employee", label: text.employee },
                { id: "status", label: text.status },
                { id: "amount", label: text.total, numeric: true },
                { id: "created", label: text.created },
              ]}
              rows={props.rows}
              action={{ label: text.view, onOpen: props.onOpen }}
            />
          ))}
        <fieldset className="app-pagination" aria-label={text.pages}>
          <AppButton
            appearance="subtle"
            onClick={props.onFirst}
            disabled={props.firstPage || state.loading}
          >
            {text.first}
          </AppButton>
          <AppButton
            appearance="subtle"
            onClick={props.onNext}
            disabled={state.page?.nextCursor == null || state.loading}
          >
            {text.next}
          </AppButton>
        </fieldset>
      </div>
    </section>
  );
}
