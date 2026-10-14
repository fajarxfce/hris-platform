import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollCalculationReview } from "../../domain/entities/payroll-calculation-review";
import type { usePayrollCalculationForm } from "../controllers/use-payroll-calculation-form";
import { payrollCalculationMessages } from "../i18n/payroll-calculation-messages";
import type { PayrollCalculationCommandState } from "../models/payroll-calculation-command-state";
import type { payrollCalculationReviewView } from "../models/payroll-calculation-view";
import { PayrollCalculationCommandStatus } from "./payroll-calculation-command-status";
export function PayrollCalculationStartPage(props: {
  state: PayrollCalculationCommandState<PayrollCalculationReview>;
  form: ReturnType<typeof usePayrollCalculationForm>;
  properties: ReturnType<typeof payrollCalculationReviewView>;
  locale: Locale;
  companyName: string;
  backTo: string;
  successTo: string | null;
  onRetry: () => void;
}) {
  const { state, form, locale } = props,
    text = payrollCalculationMessages(locale);
  return (
    <section
      className="app-report-content app-record-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={text.start}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.calculations}</Link>}
      />
      <PayrollCalculationCommandStatus
        state={state}
        locale={locale}
        successMessage={text.started}
        successTo={props.successTo}
        onRetry={props.onRetry}
        onReload={form.reload}
      />
      {state.review && (
        <>
          <AppPropertyList title={text.review} items={props.properties} />
          {state.review.blockingIssues.map((issue) => (
            <AppFailure key={issue.code} failure={issue} locale={locale} />
          ))}
        </>
      )}
      {state.stage === "reviewing" && state.review?.availableActions.includes("START") && (
        <form className="app-editor-form" aria-label={text.start} onSubmit={form.submit} noValidate>
          <p>{text.startNote}</p>
          <AppTextField
            label={text.due}
            type="date"
            required
            min={`${state.review.period.earningsMonth}-01`}
            {...form.due}
            hint={text.dueHint}
            error={form.invalidDue ? text.invalidDue : undefined}
          />
          <AppTextField
            label={text.reference}
            required
            maxLength={200}
            {...form.reference}
            error={form.invalidReference ? text.invalidReference : undefined}
          />
          <AppTextArea
            label={text.reason}
            rows={3}
            required
            maxLength={1000}
            {...form.reason}
            error={form.invalidReason ? text.invalidReason : undefined}
          />
          <div className="app-form-actions">
            <AppButton type="submit" appearance="primary">
              {text.start}
            </AppButton>
          </div>
        </form>
      )}
      {state.stage === "reviewing" && !state.review?.availableActions.includes("START") && (
        <div>
          <AppButton onClick={form.reload}>{text.reload}</AppButton>
        </div>
      )}
    </section>
  );
}
