import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppConfirmationDialog } from "../../../../core/presentation/components/app-confirmation-dialog";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { CompanyMembership } from "../../../identity/domain/entities/session";
import { ReportExportCommandStatus } from "../components/report-export-command-status";
import { ReportExportSelection } from "../components/report-export-selection";
import { reportExportMessages } from "../i18n/report-export-messages";
import type { ReportExportState } from "../models/report-export-state";
export function ReportExportDetailPage({
  state,
  companies,
  companyName,
  locale,
  backTo,
  properties,
  canCancel,
  canResume,
  canDownload,
  onRefresh,
  onReview,
  onDismiss,
  onConfirm,
  onRetry,
  onDownload,
  onCancelDownload,
}: {
  state: ReportExportState;
  companies: readonly CompanyMembership[];
  companyName: string;
  locale: Locale;
  backTo: string;
  properties: readonly { label: string; value: string }[];
  canCancel: boolean;
  canResume: boolean;
  canDownload: boolean;
  onRefresh: () => void;
  onReview: (kind: "cancel" | "resume") => void;
  onDismiss: () => void;
  onConfirm: () => void;
  onRetry: () => void;
  onDownload: () => void;
  onCancelDownload: () => void;
}) {
  const text = reportExportMessages(locale),
    shared = messages(locale),
    busy = state.stage === "saving" || state.stage === "loading",
    detail = state.detail;
  return (
    <section className="app-report-content" aria-busy={busy}>
      <AppPageHeader
        title={text.review}
        context={companyName}
        actions={
          <>
            <Link to={backTo}>{text.back}</Link>
            <AppButton
              disabled={busy || state.stage === "unconfirmed" || state.download === "downloading"}
              onClick={onRefresh}
            >
              {shared.refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <ReportExportCommandStatus state={state} locale={locale} openTo={null} onRetry={onRetry} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {detail && (
        <>
          <ReportExportSelection
            selection={detail.selection}
            companies={companies}
            locale={locale}
          />
          <AppPropertyList title={text.status} items={properties} />
          {detail.failureCode && (
            <AppFailure
              failure={{ code: detail.failureCode, fields: {}, parameters: {} }}
              locale={locale}
            />
          )}
          <p>{text.retention}</p>
          {detail.status !== "READY" && !detail.expired && <p>{text.stoppedHint}</p>}
          <div className="app-form-actions">
            {(["cancel", "resume"] as const).map(
              (kind) =>
                (kind === "cancel" ? canCancel : canResume) && (
                  <AppConfirmationDialog
                    key={kind}
                    open={state.confirmation === kind}
                    title={kind === "cancel" ? text.confirmCancel : text.confirmResume}
                    message={kind === "cancel" ? text.cancelHint : text.resumeHint}
                    confirmLabel={text[kind]}
                    dismissLabel={shared.cancel}
                    busy={busy}
                    onConfirm={onConfirm}
                    onDismiss={onDismiss}
                    trigger={
                      <AppButton
                        disabled={state.stage !== "ready" || state.download === "downloading"}
                        onClick={() => onReview(kind)}
                      >
                        {text[kind]}
                      </AppButton>
                    }
                  />
                ),
            )}
            {canDownload && (
              <AppButton
                appearance="primary"
                disabled={state.stage !== "ready" || state.download === "downloading"}
                onClick={onDownload}
              >
                {text.download}
              </AppButton>
            )}
            {state.download === "downloading" && (
              <>
                <span role="status">{text.downloading}</span>
                <AppButton onClick={onCancelDownload}>{text.cancelDownload}</AppButton>
              </>
            )}
            {state.download === "started" && <p role="status">{text.downloaded}</p>}
          </div>
        </>
      )}
    </section>
  );
}
