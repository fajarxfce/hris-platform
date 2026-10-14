import { Link } from "react-router-dom";
import type { Failure } from "../../../../core/domain/result";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { usePayrollReviewReasonForm } from "../controllers/use-payroll-review-reason-form";
import { payrollReviewMessages } from "../i18n/payroll-review-messages";
import type { PayrollReviewCommandState } from "../models/payroll-review-command-state";
import { PayrollReviewCommandStatus } from "./payroll-review-command-status";
export function PayrollReviewCommandPage(props: {
  state: PayrollReviewCommandState<unknown>;
  form: ReturnType<typeof usePayrollReviewReasonForm>;
  title: string;
  note: string;
  label: string;
  canSubmit: boolean;
  requiredReason: boolean;
  properties: readonly Readonly<{ label: string; value: string }>[];
  issues: readonly Failure[];
  locale: Locale;
  companyName: string;
  backTo: string;
  backLabel: string;
  successTo: string | null;
  successMessage: string;
  onRetry: () => void;
}) {
  const { state, form, locale } = props,
    text = payrollReviewMessages(locale);
  return (
    <section
      className="app-report-content app-record-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={props.title}
        context={props.companyName}
        actions={<Link to={props.backTo}>{props.backLabel}</Link>}
      />
      <PayrollReviewCommandStatus
        state={state}
        locale={locale}
        successMessage={props.successMessage}
        successTo={props.successTo}
        onRetry={props.onRetry}
        onReload={form.reload}
      />
      {state.review !== null && <AppPropertyList title={text.review} items={props.properties} />}
      {props.issues.map((issue) => (
        <AppFailure key={issue.code} failure={issue} locale={locale} />
      ))}
      {state.stage === "reviewing" && props.canSubmit && (
        <form
          className="app-editor-form"
          aria-label={props.title}
          onSubmit={form.submit}
          noValidate
        >
          <p>{props.note}</p>
          <AppTextArea
            label={props.requiredReason ? text.reason : text.optionalReason}
            rows={3}
            required={props.requiredReason}
            maxLength={1000}
            {...form.reason}
            error={form.invalidReason ? text.invalidReason : undefined}
          />
          <div className="app-form-actions">
            <AppButton type="submit" appearance="primary">
              {props.label}
            </AppButton>
          </div>
        </form>
      )}
    </section>
  );
}
