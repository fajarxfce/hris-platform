import { MessageBar, MessageBarBody, Text } from "@fluentui/react-components";
import { Add20Regular } from "@fluentui/react-icons";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { usePayrollPaymentPreparationForm } from "../controllers/use-payroll-payment-preparation-form";
import { payrollPaymentMessages } from "../i18n/payroll-payment-messages";
import { payrollPaymentPreparationMessages } from "../i18n/payroll-payment-preparation-messages";
import type { PayrollPaymentPreparationState } from "../models/payroll-payment-preparation-state";
import type { payrollPaymentPreparationView } from "../models/payroll-payment-preparation-view";

export function PayrollPaymentPreparationPage(props: {
  state: PayrollPaymentPreparationState;
  form: ReturnType<typeof usePayrollPaymentPreparationForm>;
  view: ReturnType<typeof payrollPaymentPreparationView>;
  companyName: string;
  locale: Locale;
  backTo: string;
  savedTo: string | null;
  onAdd: () => void;
  onDestination: (id: string) => void;
  onEdit: () => void;
  onConfirm: () => void;
  onRetry: () => void;
  panel: ReactNode;
}) {
  const { state, form, view, locale } = props,
    text = payrollPaymentPreparationMessages(locale),
    common = payrollPaymentMessages(locale);
  return (
    <>
      <section
        className="app-report-content app-record-editor"
        aria-busy={state.stage === "loading" || state.stage === "submitting"}
      >
        <AppPageHeader
          title={state.review ? text.review : text.title}
          context={props.companyName}
          actions={<Link to={props.backTo}>{text.back}</Link>}
        />
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.stage === "submitting" && <AppLoading label={text.saving} />}
        {state.stage === "unavailable" && (
          <div>
            <AppButton onClick={form.restart}>{text.restart}</AppButton>
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
              {text.saved} {props.savedTo && <Link to={props.savedTo}>{text.details}</Link>}
            </MessageBarBody>
          </MessageBar>
        )}
        {state.stage === "editing" && (
          <form
            className="app-editor-form"
            aria-label={text.title}
            onSubmit={form.submit}
            noValidate
          >
            <AppTextField
              label={text.name}
              {...form.title}
              maxLength={160}
              required
              error={form.titleInvalid ? text.nameError : undefined}
            />
            <AppTextArea
              label={text.reason}
              {...form.reason}
              maxLength={1000}
              rows={3}
              required
              error={form.reasonInvalid ? text.reasonError : undefined}
            />
            <AppPropertyList title={text.selection} items={view.properties} />
            <div className="app-form-actions">
              <AppButton
                icon={<Add20Regular />}
                onClick={props.onAdd}
                disabled={state.items.length >= 100}
              >
                {text.add}
              </AppButton>
              <Text>{text.limit}</Text>
            </div>
            {state.items.length === 0 ? (
              <Text role="status">{text.empty}</Text>
            ) : (
              <AppResourceTable
                title={text.selection}
                minWidth={880}
                columns={[
                  { id: "taxMonth", label: text.taxMonth },
                  { id: "employee", label: common.employee },
                  { id: "destination", label: text.destination },
                  { id: "amount", label: common.amount, numeric: true },
                ]}
                rows={view.rows}
                action={{ label: text.editDestination, onOpen: props.onDestination }}
              />
            )}
            <div className="app-form-actions">
              <AppButton type="submit" appearance="primary" disabled={!view.canReview}>
                {text.reviewAction}
              </AppButton>
            </div>
          </form>
        )}
        {state.review && (
          <>
            <AppPropertyList title={state.review.title} items={view.properties} />
            <p>{text.reviewNote}</p>
            <AppResourceTable
              title={common.instructions}
              minWidth={900}
              columns={[
                { id: "taxMonth", label: text.taxMonth },
                { id: "employee", label: common.employee },
                { id: "bank", label: common.bank },
                { id: "name", label: common.accountName },
                { id: "account", label: common.accountNumber },
                { id: "amount", label: common.amount, numeric: true },
              ]}
              rows={view.reviewRows}
            />
            <section className="app-resource-panel" aria-label={text.reason}>
              <h2>{text.reason}</h2>
              <p>{state.review.reason}</p>
            </section>
            <p>{text.immutableNote}</p>
            {state.stage === "reviewing" && (
              <div className="app-form-actions">
                <AppButton appearance="primary" onClick={props.onConfirm}>
                  {text.create}
                </AppButton>
                <AppButton onClick={props.onEdit}>{text.edit}</AppButton>
              </div>
            )}
          </>
        )}
      </section>
      {props.panel}
    </>
  );
}
