import { Add20Regular } from "@fluentui/react-icons";
import type { ReactNode } from "react";
import type { Failure } from "../../../../core/domain/result";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { usePayrollInputTermFields } from "../controllers/use-payroll-input-term-fields";
import { payrollInputFieldError, payrollInputMessages } from "../i18n/payroll-input-messages";
export function PayrollInputTermsFields({
  form,
  failure,
  locale,
  earnings,
  deductions,
  days,
}: {
  form: ReturnType<typeof usePayrollInputTermFields>;
  failure: Failure | null;
  locale: Locale;
  earnings: ReactNode;
  deductions: ReactNode;
  days: ReactNode;
}) {
  const text = payrollInputMessages(locale);
  return (
    <>
      <fieldset className="app-editor-section app-compensation-section">
        <legend>{text.earnings}</legend>
        <p className="app-muted">{text.amountHint}</p>
        <AppTextField
          label={text.nonCashTaxable}
          {...form.nonCashTaxable}
          inputMode="decimal"
          maxLength={14}
          required
          readOnly={!form.editable}
          error={payrollInputFieldError(failure, "terms.nonCashTaxable", locale)}
        />
        {payrollInputFieldError(failure, "terms.earnings", locale) && (
          <p role="alert">{payrollInputFieldError(failure, "terms.earnings", locale)}</p>
        )}
        {earnings}
        <div>
          <AppButton
            appearance="subtle"
            icon={<Add20Regular />}
            onClick={form.addEarning}
            disabled={!form.editable || form.earnings.length >= 40}
          >
            {text.addEarning}
          </AppButton>
        </div>
      </fieldset>
      <fieldset className="app-editor-section app-compensation-section">
        <legend>{text.deductions}</legend>
        {payrollInputFieldError(failure, "terms.deductions", locale) && (
          <p role="alert">{payrollInputFieldError(failure, "terms.deductions", locale)}</p>
        )}
        {deductions}
        <div>
          <AppButton
            appearance="subtle"
            icon={<Add20Regular />}
            onClick={form.addDeduction}
            disabled={!form.editable || form.deductions.length >= 20}
          >
            {text.addDeduction}
          </AppButton>
        </div>
      </fieldset>
      <fieldset className="app-editor-section app-compensation-section">
        <legend>{text.days}</legend>
        <AppTextField
          label={text.scheduledMonthUnits}
          {...form.scheduledMonthUnits}
          inputMode="decimal"
          maxLength={4}
          readOnly={!form.editable}
          error={payrollInputFieldError(failure, "terms.scheduledMonthUnits", locale)}
        />
        <p className="app-muted">{text.unitsHint}</p>
        {payrollInputFieldError(failure, "terms.dayResolutions", locale) && (
          <p role="alert">{payrollInputFieldError(failure, "terms.dayResolutions", locale)}</p>
        )}
        {days}
        <div>
          <AppButton
            appearance="subtle"
            icon={<Add20Regular />}
            onClick={form.addDay}
            disabled={!form.editable || form.days.length >= 62}
          >
            {text.addDay}
          </AppButton>
        </div>
      </fieldset>
    </>
  );
}
