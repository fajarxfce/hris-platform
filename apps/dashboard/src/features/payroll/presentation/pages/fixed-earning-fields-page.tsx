import { Delete20Regular } from "@fluentui/react-icons";
import type { Failure } from "../../../../core/domain/result";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { useFixedEarningForm } from "../controllers/use-fixed-earning-form";
import { compensationFieldError, compensationMessages } from "../i18n/compensation-messages";
export function FixedEarningFieldsPage({
  form,
  index,
  editable,
  failure,
  locale,
  onRemove,
}: {
  form: ReturnType<typeof useFixedEarningForm>;
  index: number;
  editable: boolean;
  failure: Failure | null;
  locale: Locale;
  onRemove: (index: number) => void;
}) {
  const text = compensationMessages(locale);
  return (
    <fieldset className="app-compensation-earning">
      <legend>
        {text.fixedEarnings} {index + 1}
      </legend>
      <div className="app-editor-fields">
        <AppTextField
          label={text.code}
          {...form.code}
          maxLength={32}
          required
          readOnly={!editable}
          error={compensationFieldError(failure, `terms.fixedEarnings[${index}].code`, locale)}
        />
        <AppTextField
          label={text.name}
          {...form.name}
          maxLength={100}
          required
          readOnly={!editable}
          error={compensationFieldError(failure, `terms.fixedEarnings[${index}].name`, locale)}
        />
        <AppTextField
          label={text.amount}
          {...form.amount}
          inputMode="decimal"
          maxLength={14}
          required
          readOnly={!editable}
          error={compensationFieldError(failure, `terms.fixedEarnings[${index}].amount`, locale)}
        />
        <AppCheckbox
          label={text.taxable}
          checked={form.taxable.value}
          onChange={(_, data) => form.taxable.onChange(data.checked === true)}
          disabled={!editable}
        />
      </div>
      <AppButton
        appearance="subtle"
        icon={<Delete20Regular />}
        onClick={() => onRemove(index)}
        disabled={!editable}
      >
        {text.removeEarning}
      </AppButton>
    </fieldset>
  );
}
