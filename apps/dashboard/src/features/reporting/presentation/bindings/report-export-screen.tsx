import { useContext, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess, CompanyMembership } from "../../../identity/domain/entities/session";
import type { ReportExportDataset } from "../../domain/entities/report-export";
import type { ReportSelection } from "../../domain/entities/report-selection";
import { canReadReportKind } from "../../domain/policies/report-catalogue-policy";
import {
  canCancelReportExport,
  canResumeReportExport,
  reportExportExpired,
  supportsReportDetail,
} from "../../domain/policies/report-export-policy";
import type { ReportExportUseCases } from "../contracts/report-export-use-cases";
import { ReportExportController } from "../controllers/report-export-controller";
import { reportExportMessages } from "../i18n/report-export-messages";
import {
  reportExportHistoryPath,
  reportExportPath,
  reportExportSourcePath,
} from "../models/report-export-route";
import { reportFilterSeed } from "../models/saved-report-filter-route";
import { ReportExportCreatePage } from "../pages/report-export-create-page";
import { ReportExportDetailPage } from "../pages/report-export-detail-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  actions: ReportExportUseCases;
  companyName: string;
  companies: readonly CompanyMembership[];
  timezone: string;
  locale: Locale;
  nextIdentifier: () => string;
  creating: boolean;
};
export function ReportExportScreen(props: Props) {
  const { exportId = "" } = useParams(),
    [query] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const seed = useMemo(
    () =>
      props.creating && query.has("kind") ? reportFilterSeed(props.access, today, query) : null,
    [props.creating, props.access, today, query],
  );
  if (query.get("company") !== props.access.companyId)
    return <Navigate replace to={reportExportHistoryPath(props.access.companyId)} />;
  if (props.creating && !seed)
    return (
      <AppFailure
        failure={{ code: "invalid_report_filter_parameters", fields: {}, parameters: {} }}
        locale={props.locale}
      />
    );
  return (
    <ExportBinding
      key={`${props.accountId}:${props.access.companyId}:${props.creating ? query.toString() : exportId}`}
      {...props}
      id={props.creating ? null : exportId}
      selection={
        seed
          ? {
              kind: seed.kind,
              definitionVersion: seed.definitionVersion,
              parameters: seed.parameters,
            }
          : null
      }
    />
  );
}
function ExportBinding(props: Props & { id: string | null; selection: ReportSelection | null }) {
  const visible = useContext(WorkspaceVisibilityContext),
    [dataset, setDataset] = useState<ReportExportDataset>("SUMMARY");
  const controller = useMemo(
    () => new ReportExportController(props.actions, props.access, props.id, props.nextIdentifier),
    [props.actions, props.access, props.id, props.nextIdentifier],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => controller.setVisible(visible), [controller, visible]);
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useWorkspaceRevalidation(state.failure);
  useNavigationProtection(
    state.stage === "saving" || state.download === "downloading"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : "none",
  );
  const backTo = reportExportHistoryPath(props.access.companyId);
  if (props.selection)
    return (
      <ReportExportCreatePage
        state={state}
        selection={props.selection}
        dataset={dataset}
        companies={props.companies}
        companyName={props.companyName}
        locale={props.locale}
        allowed={canReadReportKind(props.access.permissions, props.selection.kind)}
        backTo={backTo}
        sourceTo={reportExportSourcePath(props.access.companyId, props.selection)}
        openTo={state.receipt ? reportExportPath(props.access.companyId, state.receipt.id) : null}
        onDataset={(value) => {
          if (
            state.stage === "ready" &&
            (value === "SUMMARY" ||
              (value === "DETAIL" && props.selection && supportsReportDetail(props.selection.kind)))
          )
            setDataset(value);
        }}
        onStart={() => {
          if (props.selection) void controller.start({ ...props.selection, dataset });
        }}
        onRetry={controller.retry}
      />
    );
  const detail = state.detail,
    text = reportExportMessages(props.locale),
    date = new Intl.DateTimeFormat(props.locale, {
      timeZone: props.timezone,
      dateStyle: "medium",
      timeStyle: "short",
    }),
    number = new Intl.NumberFormat(props.locale),
    now = Date.now();
  const properties = detail
    ? [
        { label: text.reference, value: detail.id },
        {
          label: text.status,
          value: detail.retiredAt
            ? text.retired
            : reportExportExpired(detail, now)
              ? text.expired
              : text[detail.status],
        },
        { label: text.dataset, value: text[detail.dataset] },
        { label: text.created, value: date.format(new Date(detail.createdAt)) },
        { label: text.expiry, value: date.format(new Date(detail.expiresAt)) },
        { label: text.rows, value: number.format(detail.completedRows) },
        { label: text.bytes, value: number.format(detail.bytes) },
        { label: text.attempt, value: number.format(detail.attempt) },
        {
          label: text.job,
          value: `${text[detail.jobStatus]}${detail.cancellationRequested ? ` / ${text.cancellation}` : ""}`,
        },
      ]
    : [];
  const source = detail && canReadReportKind(props.access.permissions, detail.selection.kind);
  return (
    <ReportExportDetailPage
      state={state}
      properties={properties}
      companies={props.companies}
      companyName={props.companyName}
      locale={props.locale}
      backTo={backTo}
      canCancel={!!detail && canCancelReportExport(detail)}
      canResume={!!detail && !!source && canResumeReportExport(detail, now)}
      canDownload={
        !!detail && !!source && detail.status === "READY" && !reportExportExpired(detail, now)
      }
      onRefresh={controller.refresh}
      onReview={controller.review}
      onDismiss={controller.dismiss}
      onConfirm={controller.confirm}
      onRetry={controller.retry}
      onDownload={controller.download}
      onCancelDownload={controller.cancelDownload}
    />
  );
}
