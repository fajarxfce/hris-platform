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
import { expensePaymentBatchStatuses } from "../../domain/entities/expense-payment-batch";
import type { useExpensePaymentFilters } from "../controllers/use-expense-payment-filters";
import type { useExpensePayments } from "../controllers/use-expense-payments";
import { expensePaymentMessages } from "../i18n/expense-payment-messages";
import { expensePreparationMessages } from "../i18n/expense-preparation-messages";
import type { expensePaymentRows } from "../models/expense-payment-view";
export function ExpensePaymentsPage(props: {
  state: ReturnType<typeof useExpensePayments>;
  filters: ReturnType<typeof useExpensePaymentFilters>;
  rows: ReturnType<typeof expensePaymentRows>;
  companyName: string;
  canPrepare: boolean;
  onPrepare: () => void;
  locale: Locale;
  firstPage: boolean;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const { state, filters, locale } = props,
    text = expensePaymentMessages(locale);
  return (
    <section aria-busy={state.loading}>
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={
          <div className="app-form-actions">
            {props.canPrepare && (
              <AppButton appearance="primary" icon={<Add20Regular />} onClick={props.onPrepare}>
                {expensePreparationMessages(locale).title}
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
          </div>
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
              {expensePaymentBatchStatuses.map((status) => (
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
              minWidth={980}
              columns={[
                { id: "title", label: text.batchTitle },
                { id: "status", label: text.status },
                { id: "total", label: text.total, numeric: true },
                { id: "items", label: text.items, numeric: true },
                { id: "pending", label: text.pending, numeric: true },
                { id: "created", label: text.created },
              ]}
              rows={props.rows}
              action={{ label: text.view, onOpen: props.onOpen }}
            />
          ))}
        <fieldset className="app-pagination" aria-label={text.pages}>
          <AppButton
            appearance="subtle"
            disabled={props.firstPage || state.loading}
            onClick={props.onFirst}
          >
            {text.first}
          </AppButton>
          <AppButton
            appearance="subtle"
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
