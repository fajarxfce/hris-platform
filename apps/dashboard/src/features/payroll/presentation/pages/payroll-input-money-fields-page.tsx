import { Delete20Regular } from "@fluentui/react-icons";
import type { Failure } from "../../../../core/domain/result";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type {
  usePayrollInputDeduction,
  usePayrollInputEarning,
} from "../controllers/use-payroll-input-line";
import { payrollInputFieldError, payrollInputMessages } from "../i18n/payroll-input-messages";
export function PayrollInputMoneyFieldsPage({
  form,
  category,
  index,
  editable,
  failure,
  locale,
  onRemove,
}: {
  form:
    | ReturnType<typeof usePayrollInputEarning>
    | (ReturnType<typeof usePayrollInputDeduction> & { taxable?: undefined });
  category: "earnings" | "deductions";
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
        {text[category]} {index + 1}
      </legend>
      <div className="app-editor-fields">
        <AppTextField
          label={text.code}
          {...form.code}
          maxLength={32}
          required
          readOnly={!editable}
          error={payrollInputFieldError(failure, `terms.${category}[${index}].code`, locale)}
        />
        <AppTextField
          label={text.name}
          {...form.name}
          maxLength={100}
          required
          readOnly={!editable}
          error={payrollInputFieldError(failure, `terms.${category}[${index}].name`, locale)}
        />
        <AppTextField
          label={text.amount}
          {...form.amount}
          inputMode="decimal"
          maxLength={14}
          required
          readOnly={!editable}
          error={payrollInputFieldError(failure, `terms.${category}[${index}].amount`, locale)}
        />
        {form.taxable && (
          <AppCheckbox
            label={text.taxable}
            checked={form.taxable.value}
            onChange={(_, data) => form.taxable?.onChange(data.checked === true)}
            disabled={!editable}
          />
        )}
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
