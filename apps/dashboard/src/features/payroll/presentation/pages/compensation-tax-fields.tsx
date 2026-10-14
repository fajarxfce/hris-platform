import type { Failure } from "../../../../core/domain/result";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { taxTreatments } from "../../domain/entities/compensation-terms";
import { ptkpStatuses, taxResidencies } from "../../domain/entities/payroll-tax-registration";
import type { useCompensationForm } from "../controllers/use-compensation-form";
import { compensationFieldError, compensationMessages } from "../i18n/compensation-messages";
export function CompensationTaxFields({
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
      <legend>{text.tax}</legend>
      <div className="app-editor-fields">
        <AppSelect
          label={text.treatment}
          {...form.treatment}
          disabled={!form.editable}
          required
          error={compensationFieldError(failure, "terms.treatment", locale)}
        >
          <option value="">{text.choose}</option>
          {taxTreatments.map((value) => (
            <option key={value} value={value}>
              {text[value]}
            </option>
          ))}
        </AppSelect>
        <AppSelect
          label={text.residency}
          {...form.residency}
          onChange={(_, data) => form.changeResidency(data.value)}
          disabled={!form.editable}
          required
          error={compensationFieldError(failure, "terms.tax.residency", locale)}
        >
          <option value="">{text.choose}</option>
          {taxResidencies.map((value) => (
            <option key={value} value={value}>
              {text[value]}
            </option>
          ))}
        </AppSelect>
        <AppSelect
          label={text.ptkp}
          {...form.ptkp}
          disabled={!form.editable}
          required
          error={compensationFieldError(failure, "terms.tax.ptkp", locale)}
        >
          <option value="">{text.choose}</option>
          {ptkpStatuses.map((value) => (
            <option key={value} value={value}>
              {text[value]}
            </option>
          ))}
        </AppSelect>
        <AppTextField
          label={text.residenceCountry}
          {...form.residenceCountry}
          readOnly={!form.editable}
          required
          maxLength={2}
          autoCapitalize="characters"
          error={compensationFieldError(failure, "terms.tax.residenceCountry", locale)}
        />
        <AppTextField
          label={text.verifiedOn}
          {...form.verifiedOn}
          readOnly={!form.editable}
          required
          type="date"
          min="1900-01-01"
          max="2100-12-31"
          error={compensationFieldError(failure, "terms.tax.verifiedOn", locale)}
        />
        <AppTextField
          label={text.verificationReference}
          {...form.verificationReference}
          readOnly={!form.editable}
          required
          maxLength={200}
          error={compensationFieldError(failure, "terms.tax.verificationReference", locale)}
        />
        {form.showSubjectivePeriod && (
          <>
            <AppTextField
              label={text.subjectiveFrom}
              {...form.subjectiveFrom}
              readOnly={!form.editable}
              type="date"
              min="1900-01-01"
              max="2100-12-31"
              error={compensationFieldError(failure, "terms.tax.subjectiveFrom", locale)}
            />
            <AppTextField
              label={text.subjectiveUntil}
              {...form.subjectiveUntil}
              readOnly={!form.editable}
              type="date"
              min="1900-01-01"
              max="2100-12-31"
              error={compensationFieldError(failure, "terms.tax.subjectiveUntil", locale)}
            />
          </>
        )}
      </div>
    </fieldset>
  );
}
