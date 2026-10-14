import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollCalculationRecoveryAction } from "../../domain/entities/payroll-calculation-command";
import type { PayrollRunDetails } from "../../domain/entities/payroll-run-details";
import type { usePayrollRunRecoveryForm } from "../controllers/use-payroll-run-recovery-form";
import { payrollCalculationMessages } from "../i18n/payroll-calculation-messages";
import type { PayrollCalculationCommandState } from "../models/payroll-calculation-command-state";
import type { payrollRunProperties } from "../models/payroll-calculation-view";
import { PayrollCalculationCommandStatus } from "./payroll-calculation-command-status";
export function PayrollRunRecoveryPage(props: {
  state: PayrollCalculationCommandState<PayrollRunDetails>;
  action: PayrollCalculationRecoveryAction;
  form: ReturnType<typeof usePayrollRunRecoveryForm>;
  properties: ReturnType<typeof payrollRunProperties>;
  locale: Locale;
  companyName: string;
  backTo: string;
  onRetry: () => void;
}) {
  const { state, form, locale, action } = props,
    text = payrollCalculationMessages(locale);
  return (
    <section
      className="app-report-content app-record-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={text[action]}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.openRun}</Link>}
      />
      <PayrollCalculationCommandStatus
        state={state}
        locale={locale}
        successMessage={action === "resume" ? text.resumed : text.abandoned}
        successTo={props.backTo}
        onRetry={props.onRetry}
        onReload={form.reload}
      />
      {state.review && <AppPropertyList title={text.review} items={props.properties} />}
      {state.stage === "reviewing" && state.review && (
        <form
          className="app-editor-form"
          aria-label={text[action]}
          onSubmit={form.submit}
          noValidate
        >
          <p>{action === "resume" ? text.resumeNote : text.abandonNote}</p>
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
              {text[action]}
            </AppButton>
          </div>
        </form>
      )}
    </section>
  );
}
