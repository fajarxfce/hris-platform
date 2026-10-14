import { Delete20Regular } from "@fluentui/react-icons";
import type { Failure } from "../../../../core/domain/result";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import {
  payrollDayDispositions,
  payrollDayPortions,
} from "../../domain/entities/payroll-input-terms";
import type { usePayrollInputDay } from "../controllers/use-payroll-input-line";
import { payrollInputFieldError, payrollInputMessages } from "../i18n/payroll-input-messages";
export function PayrollInputDayFieldsPage({
  form,
  index,
  editable,
  failure,
  locale,
  onRemove,
}: {
  form: ReturnType<typeof usePayrollInputDay>;
  index: number;
  editable: boolean;
  failure: Failure | null;
  locale: Locale;
  onRemove: (index: number) => void;
}) {
  const text = payrollInputMessages(locale);
  return (
    <fieldset className="app-compensation-earning">
      <legend>
        {text.days} {index + 1}
      </legend>
      <div className="app-editor-fields">
        <AppTextField
          label={text.workDate}
          {...form.workDate}
          type="date"
          required
          readOnly={!editable}
          error={payrollInputFieldError(failure, `terms.dayResolutions[${index}].workDate`, locale)}
        />
        <AppSelect
          label={text.portion}
          {...form.portion}
          required
          disabled={!editable}
          error={payrollInputFieldError(failure, `terms.dayResolutions[${index}].portion`, locale)}
        >
          <option value="">{text.select}</option>
          {payrollDayPortions.map((value) => (
            <option key={value} value={value}>
              {text[value]}
            </option>
          ))}
        </AppSelect>
        <AppSelect
          label={text.disposition}
          {...form.disposition}
          required
          disabled={!editable}
          error={payrollInputFieldError(
            failure,
            `terms.dayResolutions[${index}].disposition`,
            locale,
          )}
        >
          <option value="">{text.select}</option>
          {payrollDayDispositions.map((value) => (
            <option key={value} value={value}>
              {text[value]}
            </option>
          ))}
        </AppSelect>
        <AppTextField
          label={text.reference}
          {...form.reference}
          maxLength={200}
          required
          readOnly={!editable}
          error={payrollInputFieldError(
            failure,
            `terms.dayResolutions[${index}].reference`,
            locale,
          )}
        />
      </div>
      <AppButton
        appearance="subtle"
        icon={<Delete20Regular />}
        onClick={() => onRemove(index)}
        disabled={!editable}
      >
        {text.remove}
      </AppButton>
    </fieldset>
  );
}
