import type { ReactNode } from "react";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppConfirmationDialog } from "../../../../core/presentation/components/app-confirmation-dialog";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { usePayrollAmendmentCorrectionForm } from "../controllers/use-payroll-amendment-correction-form";
import { payrollAmendmentEditorMessages } from "../i18n/payroll-amendment-editor-messages";
export function PayrollAmendmentCorrectionPanel({
  form,
  locale,
  month,
  compensation,
  input,
  canRemove,
  onRemove,
}: {
  form: ReturnType<typeof usePayrollAmendmentCorrectionForm>;
  locale: Locale;
  month: string;
  compensation: ReactNode;
  input: ReactNode;
  canRemove: boolean;
  onRemove: () => void;
}) {
  const text = payrollAmendmentEditorMessages(locale);
  return (
    <AppDetailsPanel
      open
      title={`${text.change} · ${month}`}
      closeLabel={text.close}
      onClose={form.close}
      width={760}
    >
      <AppFailure failure={form.failure} locale={locale} />
      <form className="app-editor-form app-amendment-month-form" onSubmit={form.submit} noValidate>
        <AppCheckbox
          label={text.includeCompensation}
          checked={form.withCompensation}
          onChange={(_event, data) => form.changeCompensation(data.checked === true)}
        />
        {form.withCompensation && (
          <>
            <p className="app-muted">{text.taxRetained}</p>
            {compensation}
          </>
        )}
        <AppCheckbox
          label={text.includeInput}
          checked={form.withInput}
          onChange={(_event, data) => form.changeInput(data.checked === true)}
        />
        {form.withInput && input}
        <div className="app-form-actions">
          <AppButton appearance="primary" type="submit">
            {text.apply}
          </AppButton>
          {canRemove && <AppButton onClick={onRemove}>{text.remove}</AppButton>}
          <AppConfirmationDialog
            open={form.discardRequested}
            busy={false}
            title={text.discardTitle}
            message={text.discardHint}
            confirmLabel={text.discard}
            dismissLabel={text.keep}
            onConfirm={form.discard}
            onDismiss={form.keepEditing}
            trigger={<AppButton onClick={form.close}>{text.close}</AppButton>}
          />
        </div>
      </form>
    </AppDetailsPanel>
  );
}
