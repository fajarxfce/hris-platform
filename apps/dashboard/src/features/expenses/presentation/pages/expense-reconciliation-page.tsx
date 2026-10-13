import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useExpenseReconciliationForm } from "../controllers/use-expense-reconciliation-form";
import { expensePaymentMessages } from "../i18n/expense-payment-messages";
import { expenseReconciliationMessages } from "../i18n/expense-reconciliation-messages";
import type { ExpenseReconciliationState } from "../models/expense-reconciliation-state";
import type { expenseReconciliationView } from "../models/expense-reconciliation-view";

export function ExpenseReconciliationPage(props: {
  state: ExpenseReconciliationState;
  form: ReturnType<typeof useExpenseReconciliationForm>;
  view: ReturnType<typeof expenseReconciliationView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  onRetry: () => void;
  onItem: (id: string) => void;
  editor: ReactNode;
}) {
  const { state, form, view, locale } = props,
    text = expenseReconciliationMessages(locale),
    common = expensePaymentMessages(locale);
  return (
    <>
      <section
        className="app-report-content app-record-editor"
        aria-busy={state.stage === "loading" || state.stage === "submitting"}
      >
        <AppPageHeader
          title={text.title}
          context={props.companyName}
          actions={<Link to={props.backTo}>{text.back}</Link>}
        />
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.stage === "submitting" && <AppLoading label={text.saving} />}
        {state.stage === "unavailable" && (
          <div>
            <AppButton onClick={form.refresh}>{messages(locale).retry}</AppButton>
          </div>
        )}
        {state.stage === "unconfirmed" && (
          <MessageBar intent="warning" layout="multiline" role="status">
            <MessageBarBody>
              {text.unconfirmed} <AppButton onClick={props.onRetry}>{text.retry}</AppButton>
            </MessageBarBody>
          </MessageBar>
        )}
        {state.stage === "saved" && (
          <MessageBar intent="success" layout="multiline" role="status">
            <MessageBarBody>
              {text.saved} <Link to={props.backTo}>{text.back}</Link>
            </MessageBarBody>
          </MessageBar>
        )}
        {view && (
          <>
            <AppPropertyList title={view.title} items={view.properties} />
            <p>{text.note}</p>
            <AppResourceTable
              title={text.pending}
              minWidth={1000}
              columns={[
                { id: "employee", label: common.employee },
                { id: "bank", label: common.bank },
                { id: "account", label: common.accountNumber },
                { id: "amount", label: common.amount, numeric: true },
                { id: "result", label: text.result },
              ]}
              rows={view.rows}
              action={{ label: text.edit, onOpen: props.onItem, disabled: !form.editable }}
            />
            {state.stage === "conflict" && (
              <MessageBar intent="warning" layout="multiline" role="status">
                <MessageBarBody>{text.conflict}</MessageBarBody>
              </MessageBar>
            )}
            <form
              className="app-editor-form"
              aria-label={text.title}
              onSubmit={form.submit}
              noValidate
            >
              <AppTextArea
                label={text.reason}
                {...form.reason}
                rows={3}
                maxLength={1000}
                required
                readOnly={!form.editable}
                error={form.reasonInvalid ? text.reasonError : undefined}
              />
              <div className="app-form-actions">
                <AppButton
                  appearance="primary"
                  type="submit"
                  disabled={!form.editable || state.results.length === 0}
                >
                  {text.submit}
                </AppButton>
                {(state.stage === "reviewing" || state.stage === "conflict") && (
                  <AppButton
                    appearance="subtle"
                    icon={<ArrowClockwise20Regular />}
                    onClick={form.refresh}
                  >
                    {text.refresh}
                  </AppButton>
                )}
              </div>
            </form>
          </>
        )}
      </section>
      {props.editor}
    </>
  );
}
