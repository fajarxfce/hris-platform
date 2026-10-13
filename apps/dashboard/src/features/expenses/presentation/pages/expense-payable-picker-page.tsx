import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import type { Failure } from "../../../../core/domain/result";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useExpensePayablePicker } from "../controllers/use-expense-payable-picker";
import { expensePaymentMessages } from "../i18n/expense-payment-messages";
import { expensePreparationMessages } from "../i18n/expense-preparation-messages";
import type { expensePayableRows } from "../models/expense-preparation-view";

export function ExpensePayablePickerPage(props: {
  state: ReturnType<typeof useExpensePayablePicker>;
  rows: ReturnType<typeof expensePayableRows>;
  selectionFailure: Failure | null;
  locale: Locale;
  onAdd: (id: string) => void;
  onClose: () => void;
}) {
  const { state, locale } = props,
    text = expensePreparationMessages(locale),
    common = expensePaymentMessages(locale);
  return (
    <AppDetailsPanel
      open
      title={text.choose}
      closeLabel={messages(locale).close}
      onClose={props.onClose}
      width={800}
    >
      <div className="app-report-content" aria-busy={state.loading}>
        <form onSubmit={state.submit} noValidate>
          <AppCommandBar label={text.filters}>
            <div className="app-form-actions">
              <AppTextField
                label={text.from}
                type="date"
                min="1900-01-01"
                max="2200-12-31"
                {...state.from}
              />
              <AppTextField
                label={text.until}
                type="date"
                min="1900-01-01"
                max="2200-12-31"
                {...state.until}
              />
              <AppButton type="submit" appearance="primary">
                {common.apply}
              </AppButton>
              <AppButton
                appearance="subtle"
                icon={<ArrowClockwise20Regular />}
                onClick={state.refresh}
                disabled={state.loading}
              >
                {messages(locale).refresh}
              </AppButton>
            </div>
            <Text>{common.range}</Text>
          </AppCommandBar>
        </form>
        <AppFailure
          failure={state.filterFailure ?? state.failure ?? props.selectionFailure}
          locale={locale}
        />
        {state.loading && <AppLoading label={messages(locale).loading} />}
        {state.page &&
          (props.rows.length === 0 ? (
            <Text role="status">{text.emptyPayables}</Text>
          ) : (
            <AppResourceTable
              title={text.choose}
              minWidth={640}
              columns={[
                { id: "claim", label: text.claim },
                { id: "employee", label: common.employee },
                { id: "amount", label: common.amount, numeric: true },
                { id: "approved", label: text.approved },
                { id: "availability", label: text.eligibility },
              ]}
              rows={props.rows}
              action={{ label: text.addOne, onOpen: props.onAdd }}
            />
          ))}
        <fieldset className="app-pagination" aria-label={text.pages}>
          <AppButton
            appearance="subtle"
            disabled={state.firstPage || state.loading}
            onClick={state.first}
          >
            {common.first}
          </AppButton>
          <AppButton
            appearance="subtle"
            disabled={state.page?.nextCursor == null || state.loading}
            onClick={state.next}
          >
            {common.next}
          </AppButton>
        </fieldset>
        <div className="app-form-actions">
          <AppButton onClick={props.onClose}>{text.done}</AppButton>
        </div>
      </div>
    </AppDetailsPanel>
  );
}
