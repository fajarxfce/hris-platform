import { Add20Regular } from "@fluentui/react-icons";
import type { ReactNode } from "react";
import type { Failure } from "../../../../core/domain/result";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { useCompensationForm } from "../controllers/use-compensation-form";
import { compensationFieldError, compensationMessages } from "../i18n/compensation-messages";
export function CompensationEarningsFields({
  form,
  failure,
  locale,
  children,
}: {
  form: ReturnType<typeof useCompensationForm>;
  failure: Failure | null;
  locale: Locale;
  children: ReactNode;
}) {
  const text = compensationMessages(locale);
  return (
    <fieldset className="app-editor-section app-compensation-section">
      <legend>{text.earnings}</legend>
      <p className="app-muted">{text.amountHint}</p>
      <div className="app-editor-fields">
        <AppTextField
          label={text.basicSalary}
          {...form.basicSalary}
          readOnly={!form.editable}
          required
          inputMode="decimal"
          maxLength={14}
          error={compensationFieldError(failure, "terms.basicSalary", locale)}
        />
      </div>
      <h3>{text.fixedEarnings}</h3>
      {compensationFieldError(failure, "terms.fixedEarnings", locale) && (
        <p role="alert">{compensationFieldError(failure, "terms.fixedEarnings", locale)}</p>
      )}
      {children}
      <div>
        <AppButton
          appearance="subtle"
          icon={<Add20Regular />}
          onClick={form.addEarning}
          disabled={!form.editable || form.earnings.length >= 20}
        >
          {text.addEarning}
        </AppButton>
      </div>
    </fieldset>
  );
}
