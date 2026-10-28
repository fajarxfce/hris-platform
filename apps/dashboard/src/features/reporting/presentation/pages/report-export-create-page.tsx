import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { CompanyMembership } from "../../../identity/domain/entities/session";
import type { ReportExportDataset } from "../../domain/entities/report-export";
import type { ReportSelection } from "../../domain/entities/report-selection";
import { supportsReportDetail } from "../../domain/policies/report-export-policy";
import { ReportExportCommandStatus } from "../components/report-export-command-status";
import { ReportExportSelection } from "../components/report-export-selection";
import { reportExportMessages } from "../i18n/report-export-messages";
import type { ReportExportState } from "../models/report-export-state";
export function ReportExportCreatePage({
  state,
  selection,
  dataset,
  companies,
  companyName,
  locale,
  allowed,
  backTo,
  sourceTo,
  openTo,
  onDataset,
  onStart,
  onRetry,
}: {
  state: ReportExportState;
  selection: ReportSelection;
  dataset: ReportExportDataset;
  companies: readonly CompanyMembership[];
  companyName: string;
  locale: Locale;
  allowed: boolean;
  backTo: string;
  sourceTo: string;
  openTo: string | null;
  onDataset: (dataset: string) => void;
  onStart: () => void;
  onRetry: () => void;
}) {
  const text = reportExportMessages(locale);
  return (
    <section
      className="app-report-content"
      aria-busy={state.stage === "saving" || state.stage === "loading"}
    >
      <AppPageHeader
        title={text.review}
        context={companyName}
        actions={
          <>
            <Link to={sourceTo}>{text.report}</Link>
            <Link to={backTo}>{text.history}</Link>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <ReportExportCommandStatus state={state} locale={locale} openTo={openTo} onRetry={onRetry} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage !== "completed" && (
        <>
          <ReportExportSelection selection={selection} companies={companies} locale={locale} />
          <p>{text.capture}</p>
          <p>{text.retention}</p>
          <p>{text.limits}</p>
          {!allowed && <p>{text.sourceUnavailable}</p>}
          <AppSelect
            label={text.dataset}
            value={dataset}
            disabled={!allowed || state.stage !== "ready"}
            onChange={(_event, data) => onDataset(data.value)}
          >
            <option value="SUMMARY">{text.SUMMARY}</option>
            {supportsReportDetail(selection.kind) && <option value="DETAIL">{text.DETAIL}</option>}
          </AppSelect>
          <div className="app-form-actions">
            <AppButton
              appearance="primary"
              onClick={onStart}
              disabled={!allowed || state.stage !== "ready"}
            >
              {text.start}
            </AppButton>
          </div>
        </>
      )}
    </section>
  );
}
