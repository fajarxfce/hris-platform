import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { usePayrollInputVerification } from "../controllers/use-payroll-input-verification";
import { payrollInputFieldError, payrollInputMessages } from "../i18n/payroll-input-messages";
import type { PayrollInputCommandState } from "../models/payroll-input-command-state";
import type { payrollInputReviewView, payrollInputView } from "../models/payroll-input-view";
import { PayrollInputCommandStatus } from "./payroll-input-command-status";
import { PayrollInputProperties } from "./payroll-input-properties";
export function PayrollInputVerificationPage({
  state,
  form,
  view,
  source,
  locale,
  companyName,
  backTo,
  onRetry,
}: {
  state: PayrollInputCommandState;
  form: ReturnType<typeof usePayrollInputVerification>;
  view: ReturnType<typeof payrollInputView> | null;
  source: ReturnType<typeof payrollInputReviewView>["source"] | null;
  locale: Locale;
  companyName: string;
  backTo: string;
  onRetry: () => void;
}) {
  const text = payrollInputMessages(locale);
  return (
    <section
      className="app-report-content app-record-editor app-compensation-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={text.verifyTitle}
        context={`${companyName} / ${state.context?.employee.name ?? text.title}`}
        actions={<Link to={backTo}>{text.backToInput}</Link>}
      />
      <PayrollInputCommandStatus
        state={state}
        locale={locale}
        backTo={backTo}
        savedText={text.verified}
        onRetry={onRetry}
        onRefresh={form.refresh}
      />
      {state.context && view && (
        <>
          <p>{text.verifyNote}</p>
          {source && <AppPropertyList title={text.source} items={source} />}
          <PayrollInputProperties view={view} locale={locale} />
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <AppTextArea
              label={text.reason}
              {...form.reason}
              required
              maxLength={1000}
              rows={2}
              readOnly={!form.editable}
              error={payrollInputFieldError(state.failure, "reason", locale)}
            />
            <div className="app-form-actions">
              <AppButton appearance="primary" type="submit" disabled={!form.editable}>
                {text.verify}
              </AppButton>
              {state.stage === "conflict" && (
                <AppButton onClick={form.refresh}>{text.reload}</AppButton>
              )}
            </div>
          </form>
        </>
      )}
    </section>
  );
}
