import { Field, MessageBar, MessageBarBody } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useExpenseDecisionForm } from "../controllers/use-expense-decision-form";
import { expenseClaimMessages } from "../i18n/expense-claim-messages";
import { expenseDecisionMessages } from "../i18n/expense-decision-messages";
import type { ExpenseDecisionIntent } from "../models/expense-decision-intent";
import type { ExpenseDecisionState } from "../models/expense-decision-state";
import type { expenseSubmissionView } from "../models/expense-submission-view";

export function ExpenseDecisionPage(props: {
  state: ExpenseDecisionState;
  form: ReturnType<typeof useExpenseDecisionForm>;
  view: ReturnType<typeof expenseSubmissionView> | null;
  intent: ExpenseDecisionIntent;
  companyName: string;
  locale: Locale;
  backTo: string;
  receiptContent: ReactNode;
  onRetry: () => void;
  onLine: (id: string | null) => void;
}) {
  const { state, form, view, locale, intent } = props;
  const text = expenseDecisionMessages(locale),
    common = expenseClaimMessages(locale);
  return (
    <>
      <section
        className="app-report-content app-record-editor app-expense-decision"
        aria-busy={state.stage === "loading" || state.stage === "submitting"}
      >
        <AppPageHeader
          title={text.titles[intent]}
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
              <div className="app-reference">
                {text.operation}: {state.operationId}
              </div>
              <AppButton onClick={props.onRetry}>{text.retry}</AppButton>
            </MessageBarBody>
          </MessageBar>
        )}
        {state.stage === "saved" && (
          <MessageBar intent="success" layout="multiline" role="status">
            <MessageBarBody>
              {text.saved[intent]} <Link to={props.backTo}>{text.view}</Link>
            </MessageBarBody>
          </MessageBar>
        )}
        {view && (
          <>
            <AppPropertyList title={view.title} items={view.properties} />
            <AppResourceTable
              title={common.items}
              minWidth={900}
              columns={[
                { id: "date", label: common.date },
                { id: "category", label: common.category },
                { id: "description", label: common.description },
                { id: "amount", label: common.amount, numeric: true },
                { id: "receipts", label: common.receipts, numeric: true },
              ]}
              rows={view.lines}
              action={{ label: common.viewLine, onOpen: props.onLine }}
            />
            {state.stage === "conflict" && (
              <MessageBar intent="warning" layout="multiline" role="status">
                <MessageBarBody>{text.conflict}</MessageBarBody>
              </MessageBar>
            )}
            <form
              className="app-editor-form"
              aria-label={text.titles[intent]}
              onSubmit={form.submit}
              noValidate
            >
              <p>{text.hints[intent]}</p>
              {form.acknowledgeRequired && (
                <MessageBar intent="warning" layout="multiline">
                  <MessageBarBody>{text.duplicate}</MessageBarBody>
                </MessageBar>
              )}
              <AppTextArea
                label={text.reason}
                {...form.reason}
                rows={4}
                maxLength={1000}
                required={form.required}
                readOnly={!form.editable}
                error={form.reasonInvalid ? text.reasonRequired : undefined}
              />
              {form.acknowledgeRequired && (
                <Field
                  validationState={form.acknowledgeInvalid ? "error" : "none"}
                  validationMessage={form.acknowledgeInvalid ? text.acknowledgementRequired : null}
                >
                  <AppCheckbox
                    label={text.acknowledgement}
                    name={form.acknowledge.name}
                    ref={form.acknowledge.ref}
                    onBlur={form.acknowledge.onBlur}
                    checked={form.acknowledge.value}
                    onChange={(_, data) => form.acknowledge.onChange(data.checked === true)}
                    required
                    disabled={!form.editable}
                  />
                </Field>
              )}
              <div className="app-form-actions">
                <AppButton appearance="primary" type="submit" disabled={!form.editable}>
                  {text.titles[intent]}
                </AppButton>
                {(state.stage === "reviewing" || state.stage === "conflict") && (
                  <AppButton
                    appearance="subtle"
                    icon={<ArrowClockwise20Regular />}
                    onClick={form.refresh}
                  >
                    {text.reload}
                  </AppButton>
                )}
              </div>
            </form>
          </>
        )}
      </section>
      <AppDetailsPanel
        open={view?.line != null}
        title={common.lineDetails}
        closeLabel={messages(locale).close}
        onClose={() => props.onLine(null)}
      >
        {view?.line && (
          <>
            <AppPropertyList title={common.lineDetails} items={view.line.properties} />
            {props.receiptContent}
          </>
        )}
      </AppDetailsPanel>
    </>
  );
}
