import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { useTaxOpeningVerification } from "../controllers/use-tax-opening-verification";
import { taxOpeningFieldError, taxOpeningMessages } from "../i18n/tax-opening-messages";
import type { TaxOpeningCommandState } from "../models/tax-opening-command-state";
import type { taxOpeningView } from "../models/tax-opening-view";
import { TaxOpeningCommandStatus } from "./tax-opening-command-status";
import { TaxOpeningProperties } from "./tax-opening-properties";
export function TaxOpeningVerificationPage({
  state,
  form,
  view,
  locale,
  companyName,
  backTo,
  onRetry,
}: {
  state: TaxOpeningCommandState;
  form: ReturnType<typeof useTaxOpeningVerification>;
  view: ReturnType<typeof taxOpeningView> | null;
  locale: Locale;
  companyName: string;
  backTo: string;
  onRetry: () => void;
}) {
  const text = taxOpeningMessages(locale);
  return (
    <section
      className="app-report-content app-record-editor app-compensation-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={text.verifyTitle}
        context={`${companyName} / ${state.context?.employee.name ?? text.title}`}
        actions={<Link to={backTo}>{text.backToTaxOpening}</Link>}
      />
      <TaxOpeningCommandStatus
        state={state}
        locale={locale}
        backTo={backTo}
        savedText={text.verified}
        onRetry={onRetry}
        onRefresh={form.refresh}
      />
      {state.context && view && (
        <>
          <p>{text.verifyNote}</p>
          <TaxOpeningProperties view={view} locale={locale} />
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
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
                {text.verify}
              </AppButton>
            </div>
          </form>
        </>
      )}
    </section>
  );
}
