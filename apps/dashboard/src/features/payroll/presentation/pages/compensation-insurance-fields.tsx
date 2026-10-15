import type { Failure } from "../../../../core/domain/result";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { accidentRisks } from "../../domain/entities/compensation-terms";
import type { useCompensationTermFields } from "../controllers/use-compensation-term-fields";
import { compensationFieldError, compensationMessages } from "../i18n/compensation-messages";
export function CompensationInsuranceFields({
  form,
  failure,
  locale,
}: {
  form: ReturnType<typeof useCompensationTermFields>;
  failure: Failure | null;
  locale: Locale;
}) {
  const text = compensationMessages(locale);
  return (
    <fieldset className="app-editor-section app-compensation-section">
      <legend>{text.insurance}</legend>
      <div className="app-editor-fields">
        <AppTextField
          label={text.insuranceWage}
          {...form.insuranceWage}
          readOnly={!form.editable}
          required
          inputMode="decimal"
          maxLength={14}
          error={compensationFieldError(failure, "terms.insuranceWage", locale)}
        />
        <AppSelect
          label={text.accidentRisk}
          {...form.accidentRisk}
          disabled={!form.editable}
          required
          error={compensationFieldError(failure, "terms.accidentRisk", locale)}
        >
          <option value="">{text.choose}</option>
          {accidentRisks.map((value) => (
            <option key={value} value={value}>
              {text[value]}
            </option>
          ))}
        </AppSelect>
        <AppTextField
          label={text.additionalHealthDependents}
          {...form.additionalHealthDependents}
          readOnly={!form.editable}
          required
          inputMode="numeric"
          maxLength={1}
          error={compensationFieldError(failure, "terms.additionalHealthDependents", locale)}
        />
      </div>
      <fieldset className="app-compensation-programs">
        <legend>{text.programs}</legend>
        {form.programs.map((program) => (
          <AppCheckbox
            key={program.id}
            label={text[program.id]}
            checked={program.checked}
            disabled={!form.editable}
            onChange={program.onChange}
          />
        ))}
        {compensationFieldError(failure, "terms.insurancePrograms", locale) && (
          <p role="alert">{compensationFieldError(failure, "terms.insurancePrograms", locale)}</p>
        )}
      </fieldset>
      {form.showInsuranceExemption && (
        <AppTextArea
          label={text.insuranceExemptionReason}
          {...form.insuranceExemptionReason}
          maxLength={1000}
          rows={2}
          required
          readOnly={!form.editable}
          error={compensationFieldError(failure, "terms.insuranceExemptionReason", locale)}
        />
      )}
    </fieldset>
  );
}
