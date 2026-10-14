import type { Failure } from "../../../../core/domain/result";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import {
  earningsRounding,
  holidayAllowanceRuleId,
  overtimeEligibility,
  overtimeRuleId,
  payrollWeekdays,
  payrollWorkWeeks,
  prorationBases,
  serviceMonthConventions,
} from "../../domain/entities/payroll-pay-basis";
import type { useCompensationForm } from "../controllers/use-compensation-form";
import { compensationFieldError, compensationMessages } from "../i18n/compensation-messages";
export function CompensationPayBasisFields({
  form,
  failure,
  locale,
}: {
  form: ReturnType<typeof useCompensationForm>;
  failure: Failure | null;
  locale: Locale;
}) {
  const text = compensationMessages(locale);
  return (
    <fieldset className="app-editor-section app-compensation-section">
      <legend>{text.payBasis}</legend>
      <AppCheckbox
        label={text.payBasisEnabled}
        checked={form.payBasisEnabled.value}
        onChange={(_, data) => form.payBasisEnabled.onChange(data.checked === true)}
        disabled={!form.editable}
      />
      {form.payBasisEnabled.value && (
        <>
          <p className="app-muted">
            {text.overtimeRule}: {overtimeRuleId} · {text.holidayAllowanceRule}:{" "}
            {holidayAllowanceRuleId}
          </p>
          <div className="app-editor-fields">
            <AppSelect
              label={text.proration}
              {...form.proration}
              disabled={!form.editable}
              required
              error={compensationFieldError(failure, "terms.payBasis.proration", locale)}
            >
              <option value="">{text.choose}</option>
              {prorationBases.map((value) => (
                <option key={value} value={value}>
                  {text[value]}
                </option>
              ))}
            </AppSelect>
            <AppSelect
              label={text.workWeek}
              {...form.workWeek}
              onChange={(_, data) => form.changeWorkWeek(data.value)}
              disabled={!form.editable}
              required
              error={compensationFieldError(failure, "terms.payBasis.workWeek", locale)}
            >
              <option value="">{text.choose}</option>
              {payrollWorkWeeks.map((value) => (
                <option key={value} value={value}>
                  {text[value]}
                </option>
              ))}
            </AppSelect>
            {form.showShortestDay && (
              <AppSelect
                label={text.shortestWorkDay}
                {...form.shortestWorkDay}
                disabled={!form.editable}
                required
                error={compensationFieldError(failure, "terms.payBasis.shortestWorkDay", locale)}
              >
                <option value="">{text.choose}</option>
                {payrollWeekdays.map((value) => (
                  <option key={value} value={value}>
                    {text[value]}
                  </option>
                ))}
              </AppSelect>
            )}
            <AppSelect
              label={text.overtimeEligibility}
              {...form.overtimeEligibility}
              onChange={(_, data) => form.changeOvertimeEligibility(data.value)}
              disabled={!form.editable}
              required
              error={compensationFieldError(failure, "terms.payBasis.overtimeEligibility", locale)}
            >
              <option value="">{text.choose}</option>
              {overtimeEligibility.map((value) => (
                <option key={value} value={value}>
                  {text[value]}
                </option>
              ))}
            </AppSelect>
            {form.showOvertimeExemption && (
              <AppTextField
                label={text.overtimeExemptionReference}
                {...form.overtimeExemptionReference}
                readOnly={!form.editable}
                required
                maxLength={200}
                error={compensationFieldError(
                  failure,
                  "terms.payBasis.overtimeExemptionReference",
                  locale,
                )}
              />
            )}
            <AppTextField
              label={text.regularNonFixedWage}
              {...form.regularNonFixedWage}
              readOnly={!form.editable}
              required
              inputMode="decimal"
              maxLength={14}
              error={compensationFieldError(failure, "terms.payBasis.regularNonFixedWage", locale)}
            />
            <AppSelect
              label={text.serviceMonthConvention}
              {...form.serviceMonthConvention}
              disabled={!form.editable}
              required
              error={compensationFieldError(
                failure,
                "terms.payBasis.serviceMonthConvention",
                locale,
              )}
            >
              <option value="">{text.choose}</option>
              {serviceMonthConventions.map((value) => (
                <option key={value} value={value}>
                  {text[value]}
                </option>
              ))}
            </AppSelect>
            <AppSelect
              label={text.rounding}
              {...form.rounding}
              disabled={!form.editable}
              required
              error={compensationFieldError(failure, "terms.payBasis.rounding", locale)}
            >
              <option value="">{text.choose}</option>
              {earningsRounding.map((value) => (
                <option key={value} value={value}>
                  {text[value]}
                </option>
              ))}
            </AppSelect>
            <AppTextField
              label={text.reviewReference}
              {...form.reviewReference}
              readOnly={!form.editable}
              required
              maxLength={200}
              error={compensationFieldError(failure, "terms.payBasis.reviewReference", locale)}
            />
          </div>
        </>
      )}
    </fieldset>
  );
}
