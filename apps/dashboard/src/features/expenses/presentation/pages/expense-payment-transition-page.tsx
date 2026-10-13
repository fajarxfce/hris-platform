import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useExpensePaymentTransitionForm } from "../controllers/use-expense-payment-transition-form";
import { expensePaymentMessages } from "../i18n/expense-payment-messages";
import { expensePaymentTransitionMessages } from "../i18n/expense-payment-transition-messages";
import type { ExpensePaymentTransitionIntent } from "../models/expense-payment-transition-intent";
import type { ExpensePaymentTransitionState } from "../models/expense-payment-transition-state";
import type { expensePaymentTransitionView } from "../models/expense-payment-transition-view";
export function ExpensePaymentTransitionPage(props: {
  state: ExpensePaymentTransitionState;
  form: ReturnType<typeof useExpensePaymentTransitionForm>;
  view: ReturnType<typeof expensePaymentTransitionView> | null;
  intent: ExpensePaymentTransitionIntent;
  companyName: string;
  locale: Locale;
  backTo: string;
  onRetry: () => void;
  onItem: (id: string | null) => void;
}) {
  const { state, form, view, locale, intent } = props,
    text = expensePaymentTransitionMessages(locale),
    common = expensePaymentMessages(locale);
  return (
    <>
      <section
        className="app-report-content app-record-editor"
        aria-busy={state.stage === "loading" || state.stage === "submitting"}
      >
        <AppPageHeader
          title={text[intent]}
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
              {text.unconfirmed}
              <AppButton onClick={props.onRetry}>{text.retry}</AppButton>
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
            <AppResourceTable
              title={text.instructions}
              minWidth={1080}
              columns={[
                { id: "employee", label: common.employee },
                { id: "name", label: common.accountName },
                { id: "bank", label: common.bank },
                { id: "account", label: common.accountNumber },
                { id: "amount", label: common.amount, numeric: true },
              ]}
              rows={view.instructions}
              action={{ label: text.viewItem, onOpen: props.onItem }}
            />
            {state.stage === "conflict" && (
              <MessageBar intent="warning" layout="multiline" role="status">
                <MessageBarBody>{text.conflict}</MessageBarBody>
              </MessageBar>
            )}
            <form
              className="app-editor-form"
              aria-label={text[intent]}
              onSubmit={form.submit}
              noValidate
            >
              <p>{intent === "release" ? text.releaseNote : text.cancelNote}</p>
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
                <AppButton appearance="primary" type="submit" disabled={!form.editable}>
                  {intent === "release" ? text.releaseAction : text.cancelAction}
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
      <AppDetailsPanel
        open={view?.item != null}
        title={text.itemDetails}
        closeLabel={messages(locale).close}
        onClose={() => props.onItem(null)}
      >
        {view?.item && <AppPropertyList title={text.itemDetails} items={view.item} />}
      </AppDetailsPanel>
    </>
  );
}
