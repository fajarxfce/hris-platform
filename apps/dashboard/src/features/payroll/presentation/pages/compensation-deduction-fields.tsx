import type { Failure } from "../../../../core/domain/result";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { useCompensationForm } from "../controllers/use-compensation-form";
import { compensationFieldError, compensationMessages } from "../i18n/compensation-messages";
export function CompensationDeductionFields({
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
      <legend>{text.deductions}</legend>
      <div className="app-editor-fields">
        <AppTextField
          label={text.additionalRetirementContribution}
          {...form.additionalRetirementContribution}
          readOnly={!form.editable}
          required
          inputMode="decimal"
          maxLength={14}
          error={compensationFieldError(failure, "terms.additionalRetirementContribution", locale)}
        />
        <AppTextField
          label={text.qualifiedDonation}
          {...form.qualifiedDonation}
          readOnly={!form.editable}
          required
          inputMode="decimal"
          maxLength={14}
          error={compensationFieldError(failure, "terms.qualifiedDonation", locale)}
        />
        <AppTextField
          label={text.otherNetDeduction}
          {...form.otherNetDeduction}
          readOnly={!form.editable}
          required
          inputMode="decimal"
          maxLength={14}
          error={compensationFieldError(failure, "terms.otherNetDeduction", locale)}
        />
      </div>
    </fieldset>
  );
}
