import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { usePayrollPeriodCancellationForm } from "../controllers/use-payroll-period-cancellation-form";
import { payrollPeriodMessages } from "../i18n/payroll-period-messages";
import type { PayrollPeriodCancellationState } from "../models/payroll-period-cancellation-state";
import type { payrollPeriodProperties } from "../models/payroll-period-view";
export function PayrollPeriodCancellationPage(props: {
  state: PayrollPeriodCancellationState;
  form: ReturnType<typeof usePayrollPeriodCancellationForm>;
  properties: ReturnType<typeof payrollPeriodProperties>;
  locale: Locale;
  companyName: string;
  backTo: string;
  onRetry: () => void;
}) {
  const { state, form, locale } = props,
    text = payrollPeriodMessages(locale);
  return (
    <section
      className="app-report-content app-record-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={text.cancelTitle}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.details}</Link>}
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "saving" && <AppLoading label={text.saving} />}
      {state.stage === "saved" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {text.cancelled} <Link to={props.backTo}>{text.details}</Link>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "unconfirmed" && (
        <MessageBar intent="warning" role="status">
          <MessageBarBody>
            {text.unconfirmed} <AppButton onClick={props.onRetry}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.review && <AppPropertyList title={text.details} items={props.properties} />}{" "}
      {(state.stage === "conflict" || state.stage === "unavailable") && (
        <div>
          <AppButton onClick={form.reload}>{text.reload}</AppButton>
        </div>
      )}
      {state.stage === "conflict" && <p>{text.conflict}</p>}
      {state.stage === "reviewing" && state.review && (
        <form
          className="app-editor-form"
          onSubmit={form.submit}
          aria-label={text.cancelTitle}
          noValidate
        >
          <p>{text.cancelNote}</p>
          <AppTextArea
            label={text.reason}
            rows={3}
            maxLength={1000}
            required
            {...form.reason}
            error={form.invalidReason ? text.invalidReason : undefined}
          />
          <div className="app-form-actions">
            <AppButton type="submit" appearance="primary">
              {text.cancel}
            </AppButton>
          </div>
        </form>
      )}
    </section>
  );
}
