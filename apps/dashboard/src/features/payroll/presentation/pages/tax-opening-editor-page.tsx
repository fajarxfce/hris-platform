import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { ptkpStatuses, taxResidencies } from "../../domain/entities/payroll-tax-registration";
import type { useTaxOpeningForm } from "../controllers/use-tax-opening-form";
import { taxOpeningFieldError, taxOpeningMessages } from "../i18n/tax-opening-messages";
import type { TaxOpeningCommandState } from "../models/tax-opening-command-state";
import { TaxOpeningCommandStatus } from "./tax-opening-command-status";
export function TaxOpeningEditorPage({
  state,
  form,
  locale,
  companyName,
  backTo,
  onRetry,
}: {
  state: TaxOpeningCommandState;
  form: ReturnType<typeof useTaxOpeningForm>;
  locale: Locale;
  companyName: string;
  backTo: string;
  onRetry: () => void;
}) {
  const text = taxOpeningMessages(locale);
  return (
    <section
      className="app-report-content app-record-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={state.context?.employee.name ?? text.prepare}
        context={`${companyName} / ${text.title}`}
        actions={<Link to={backTo}>{text.backToTaxOpening}</Link>}
      />
      <TaxOpeningCommandStatus
        state={state}
        locale={locale}
        backTo={backTo}
        savedText={text.saved}
        onRetry={onRetry}
        onRefresh={form.refresh}
      />
      {state.context && (
        <>
          <p className="app-muted">
            {text.employeeNumber}: {state.context.employee.employeeNumber} · {text.year}:{" "}
            {state.context.year}
            {state.context.current && (
              <>
                {" "}
                · {text.version}: {state.context.current.version}
              </>
            )}
          </p>
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <p>{text.entryNote}</p>
            <AppSelect
              label={text.throughMonth}
              {...form.throughMonth}
              required
              disabled={!form.editable}
              error={taxOpeningFieldError(state.failure, "terms.throughMonth", locale)}
            >
              <option value="">{text.choose}</option>
              {text.months.map((label, index) => (
                <option key={label} value={String(index)}>
                  {label}
                </option>
              ))}
            </AppSelect>
            <div className="app-payroll-period-fields">
              <AppSelect
                label={text.residency}
                {...form.residency}
                required
                disabled={!form.editable}
                error={taxOpeningFieldError(state.failure, "terms.residency", locale)}
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
                required
                disabled={!form.editable}
                error={taxOpeningFieldError(state.failure, "terms.ptkp", locale)}
              >
                <option value="">{text.choose}</option>
                {ptkpStatuses.map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
              </AppSelect>
            </div>
            <h2>{text.amounts}</h2>
            <p className="app-muted">{text.amountHint}</p>
            {taxOpeningFieldError(state.failure, "terms.history", locale) && (
              <p role="alert">{taxOpeningFieldError(state.failure, "terms.history", locale)}</p>
            )}
            <div className="app-payroll-period-fields">
              <AppTextField
                label={text.taxableGross}
                {...form.taxableGross}
                required
                inputMode="decimal"
                maxLength={15}
                readOnly={!form.editable}
                error={taxOpeningFieldError(state.failure, "terms.history.taxableGross", locale)}
              />
              <AppTextField
                label={text.retirementContributions}
                {...form.retirementContributions}
                required
                inputMode="decimal"
                maxLength={15}
                readOnly={!form.editable}
                error={taxOpeningFieldError(
                  state.failure,
                  "terms.history.retirementContributions",
                  locale,
                )}
              />
              <AppTextField
                label={text.qualifiedDonations}
                {...form.qualifiedDonations}
                required
                inputMode="decimal"
                maxLength={15}
                readOnly={!form.editable}
                error={taxOpeningFieldError(
                  state.failure,
                  "terms.history.qualifiedDonations",
                  locale,
                )}
              />
              <AppTextField
                label={text.withheld}
                {...form.withheld}
                required
                inputMode="decimal"
                maxLength={15}
                readOnly={!form.editable}
                error={taxOpeningFieldError(state.failure, "terms.history.withheld", locale)}
              />
              <AppTextField
                label={text.previousEmployerNet}
                {...form.previousEmployerNet}
                required
                inputMode="decimal"
                maxLength={15}
                readOnly={!form.editable}
                error={taxOpeningFieldError(
                  state.failure,
                  "terms.history.previousEmployerNet",
                  locale,
                )}
              />
              <AppTextField
                label={text.previousEmployerWithheld}
                {...form.previousEmployerWithheld}
                required
                inputMode="decimal"
                maxLength={15}
                readOnly={!form.editable}
                error={taxOpeningFieldError(
                  state.failure,
                  "terms.history.previousEmployerWithheld",
                  locale,
                )}
              />
            </div>
            <AppTextField
              label={text.employmentMonths}
              {...form.employmentMonths}
              required
              type="number"
              min="0"
              max="11"
              step="1"
              readOnly={!form.editable}
              error={taxOpeningFieldError(state.failure, "terms.history.employmentMonths", locale)}
            />
            <AppTextField
              label={text.reference}
              {...form.reference}
              required
              maxLength={200}
              readOnly={!form.editable}
              error={taxOpeningFieldError(state.failure, "terms.reference", locale)}
            />
            <AppTextArea
              label={text.reason}
              {...form.reason}
              required
              maxLength={1000}
              rows={2}
              readOnly={!form.editable}
              error={taxOpeningFieldError(state.failure, "reason", locale)}
            />
            <div className="app-form-actions">
              <AppButton appearance="primary" type="submit" disabled={!form.editable}>
                {text.save}
              </AppButton>
            </div>
          </form>
        </>
      )}
    </section>
  );
}
