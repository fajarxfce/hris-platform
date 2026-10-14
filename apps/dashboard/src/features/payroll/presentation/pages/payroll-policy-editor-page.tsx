import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { usePayrollPolicyForm } from "../controllers/use-payroll-policy-form";
import { payrollPolicyFieldError, payrollPolicyMessages } from "../i18n/payroll-policy-messages";
import type { PayrollPolicyEditorState } from "../models/payroll-policy-editor-state";

export function PayrollPolicyEditorPage({
  state,
  form,
  companyName,
  locale,
  backTo,
  onRetry,
}: {
  state: PayrollPolicyEditorState;
  form: ReturnType<typeof usePayrollPolicyForm>;
  companyName: string;
  locale: Locale;
  backTo: string;
  onRetry: () => void;
}) {
  const text = payrollPolicyMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content app-record-editor app-payroll-policy-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={text.title}
        context={`${companyName} / ${text.title}`}
        actions={
          <Link {...restore} to={backTo}>
            {text.back}
          </Link>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "saving" && <AppLoading label={text.save} />}
      {state.stage === "unavailable" && (
        <AppButton onClick={form.refresh}>{messages(locale).retry}</AppButton>
      )}
      {state.stage === "unconfirmed" && (
        <MessageBar layout="multiline" intent="warning" role="status">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {text.operation}: {state.operationId}
            </div>
            <AppButton onClick={onRetry}>{text.retrySave}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar layout="multiline" intent="success" role="status">
          <MessageBarBody>
            {text.saved} {text.version}: {state.receipt?.version}.{" "}
            <Link {...restore} to={backTo}>
              {text.view}
            </Link>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.context && (
        <>
          {state.context.current && (
            <p>
              {text.basedOn}: {state.context.current.version}
            </p>
          )}
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <h2>{text.range}</h2>
            <p className="app-muted">{text.periodHint}</p>
            <div className="app-editor-fields">
              <AppTextField
                label={text.from}
                {...form.effectiveFrom}
                type="month"
                min="2024-01"
                max="2100-12"
                required
                readOnly={!form.editable}
                error={payrollPolicyFieldError(state.failure, "effectiveFrom", locale)}
              />
              <AppTextField
                label={text.until}
                {...form.effectiveUntil}
                type="month"
                min="2024-01"
                max="2100-12"
                required
                readOnly={!form.editable}
                error={payrollPolicyFieldError(state.failure, "effectiveUntil", locale)}
              />
            </div>
            <h2>{text.rules}</h2>
            <div className="app-editor-fields">
              <AppSelect
                label={text.taxRule}
                {...form.incomeTaxRuleId}
                disabled={!form.editable}
                required
                error={payrollPolicyFieldError(state.failure, "incomeTaxRuleId", locale)}
              >
                {state.context.rules.map((rule) => (
                  <option key={rule.id} value={rule.id}>
                    {rule.id}
                  </option>
                ))}
              </AppSelect>
              <AppTextField
                label={text.insuranceRule}
                {...form.insuranceRuleId}
                readOnly
                error={payrollPolicyFieldError(state.failure, "insuranceRuleId", locale)}
              />
              <AppSelect
                label={text.rounding}
                {...form.contributionRounding}
                disabled={!form.editable}
                required
                error={payrollPolicyFieldError(state.failure, "contributionRounding", locale)}
              >
                <option value="HALF_UP">{text.HALF_UP}</option>
                <option value="UP">{text.UP}</option>
              </AppSelect>
            </div>
            <h2>{text.contributions}</h2>
            <div className="app-editor-fields">
              <AppTextField
                label={text.minimum}
                {...form.minimumMonthlyWage}
                inputMode="decimal"
                maxLength={14}
                required
                readOnly={!form.editable}
                hint={text.amountHint}
                error={payrollPolicyFieldError(state.failure, "minimumMonthlyWage", locale)}
              />
              <AppTextField
                label={text.healthCap}
                {...form.healthWageCap}
                inputMode="decimal"
                maxLength={14}
                required
                readOnly={!form.editable}
                hint={text.amountHint}
                error={payrollPolicyFieldError(state.failure, "healthWageCap", locale)}
              />
              <AppTextField
                label={text.pensionCap}
                {...form.pensionWageCap}
                inputMode="decimal"
                maxLength={14}
                required
                readOnly={!form.editable}
                hint={text.amountHint}
                error={payrollPolicyFieldError(state.failure, "pensionWageCap", locale)}
              />
            </div>
            <AppTextArea
              label={text.references}
              {...form.references}
              maxLength={5010}
              rows={3}
              required
              readOnly={!form.editable}
              hint={text.referenceHint}
              error={payrollPolicyFieldError(state.failure, "reviewReferences", locale)}
            />
            <AppTextArea
              label={text.reason}
              {...form.reason}
              maxLength={1000}
              rows={2}
              required
              readOnly={!form.editable}
              error={payrollPolicyFieldError(state.failure, "reason", locale)}
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
