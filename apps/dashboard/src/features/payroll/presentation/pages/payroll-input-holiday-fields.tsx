import type { Failure } from "../../../../core/domain/result";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import {
  payrollHolidayKinds,
  payrollPriorHolidayPayments,
} from "../../domain/entities/payroll-input-terms";
import type { usePayrollInputForm } from "../controllers/use-payroll-input-form";
import { payrollInputFieldError, payrollInputMessages } from "../i18n/payroll-input-messages";
export function PayrollInputHolidayFields({
  form,
  failure,
  locale,
}: {
  form: ReturnType<typeof usePayrollInputForm>;
  failure: Failure | null;
  locale: Locale;
}) {
  const text = payrollInputMessages(locale);
  return (
    <fieldset className="app-editor-section app-compensation-section">
      <legend>{text.holiday}</legend>
      <AppCheckbox
        label={text.includeHoliday}
        checked={form.holidayEnabled.value}
        onChange={(_, data) => form.holidayEnabled.onChange(data.checked === true)}
        disabled={!form.editable}
      />
      {form.holidayEnabled.value && (
        <div className="app-editor-fields">
          <AppSelect
            label={text.holidayKind}
            {...form.holidayKind}
            required
            disabled={!form.editable}
            error={payrollInputFieldError(failure, "terms.holidayAllowance.kind", locale)}
          >
            <option value="">{text.select}</option>
            {payrollHolidayKinds.map((value) => (
              <option key={value} value={value}>
                {text[value]}
              </option>
            ))}
          </AppSelect>
          <AppTextField
            label={text.holidayDate}
            {...form.holidayDate}
            type="date"
            required
            readOnly={!form.editable}
            error={payrollInputFieldError(failure, "terms.holidayAllowance.holidayDate", locale)}
          />
          <AppTextField
            label={text.serviceFrom}
            {...form.serviceFrom}
            type="date"
            required
            readOnly={!form.editable}
            error={payrollInputFieldError(
              failure,
              "terms.holidayAllowance.continuousServiceFrom",
              locale,
            )}
          />
          <AppSelect
            label={text.priorPayment}
            {...form.priorPayment}
            required
            disabled={!form.editable}
            error={payrollInputFieldError(failure, "terms.holidayAllowance.priorPayment", locale)}
          >
            <option value="">{text.select}</option>
            {payrollPriorHolidayPayments.map((value) => (
              <option key={value} value={value}>
                {text[value]}
              </option>
            ))}
          </AppSelect>
          <AppTextField
            label={text.promisedAmount}
            {...form.promisedAmount}
            inputMode="decimal"
            maxLength={14}
            readOnly={!form.editable}
            error={payrollInputFieldError(failure, "terms.holidayAllowance.promisedAmount", locale)}
          />
          <AppTextField
            label={text.reviewReference}
            {...form.holidayReference}
            required
            maxLength={200}
            readOnly={!form.editable}
            error={payrollInputFieldError(
              failure,
              "terms.holidayAllowance.reviewReference",
              locale,
            )}
          />
        </div>
      )}
    </fieldset>
  );
}
