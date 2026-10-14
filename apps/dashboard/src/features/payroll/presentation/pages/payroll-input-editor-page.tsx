import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { usePayrollInputForm } from "../controllers/use-payroll-input-form";
import { payrollInputFieldError, payrollInputMessages } from "../i18n/payroll-input-messages";
import type { PayrollInputCommandState } from "../models/payroll-input-command-state";
import type { payrollInputReviewView } from "../models/payroll-input-view";
import { PayrollInputCommandStatus } from "./payroll-input-command-status";
export function PayrollInputEditorPage({
  state,
  form,
  source,
  locale,
  companyName,
  backTo,
  onRetry,
  children,
}: {
  state: PayrollInputCommandState;
  form: ReturnType<typeof usePayrollInputForm>;
  source: ReturnType<typeof payrollInputReviewView>["source"] | null;
  locale: Locale;
  companyName: string;
  backTo: string;
  onRetry: () => void;
  children: ReactNode;
}) {
  const text = payrollInputMessages(locale);
  return (
    <section
      className="app-report-content app-record-editor app-compensation-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={state.context?.employee.name ?? text.prepare}
        context={`${companyName} / ${text.title}`}
        actions={<Link to={backTo}>{text.backToInput}</Link>}
      />
      <PayrollInputCommandStatus
        state={state}
        locale={locale}
        backTo={backTo}
        savedText={text.saved}
        onRetry={onRetry}
        onRefresh={form.refresh}
      />
      {state.context && (
        <>
          <p className="app-muted">
            {text.employeeNumber}: {state.context.employee.employeeNumber} · {text.month}:{" "}
            {state.context.earningsMonth}
            {state.context.current && (
              <>
                {" "}
                · {text.version}: {state.context.current.version}
              </>
            )}
          </p>
          {source && <AppPropertyList title={text.source} items={source} />}
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            {children}
            <AppTextField
              label={text.reviewReference}
              {...form.reviewReference}
              required
              maxLength={200}
              readOnly={!form.editable}
              error={payrollInputFieldError(state.failure, "terms.reviewReference", locale)}
            />
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
                {text.save}
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
