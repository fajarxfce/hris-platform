import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess, CompanyMembership } from "../../../identity/domain/entities/session";
import type { ReportFilterDefinition } from "../../domain/entities/saved-report-filter";
import { canReadReportKind } from "../../domain/policies/report-catalogue-policy";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { ReportFilterEditorController } from "../controllers/report-filter-editor-controller";
import { useReportFilterForm } from "../controllers/use-report-filter-form";
import {
  openSavedReportFilter,
  reportFilterSeed,
  savedReportFiltersPath,
} from "../models/saved-report-filter-route";
import { ReportFilterEditorPage } from "../pages/report-filter-editor-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  actions: ReportingUseCases;
  creating: boolean;
  companyName: string;
  companies: readonly CompanyMembership[];
  timezone: string;
  locale: Locale;
  nextIdentifier: () => string;
};
export function ReportFilterEditorScreen(props: Props) {
  const { filterId = "" } = useParams();
  const [query] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const seed = useMemo(
    () => reportFilterSeed(props.access, today, props.creating ? query : new URLSearchParams()),
    [props.access, today, props.creating, query],
  );
  if (query.get("company") !== props.access.companyId)
    return <Navigate replace to={savedReportFiltersPath(props.access.companyId)} />;
  if (!seed)
    return (
      <AppFailure
        failure={{ code: "invalid_report_filter_parameters", fields: {}, parameters: {} }}
        locale={props.locale}
      />
    );
  return (
    <ReportFilterEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${props.creating ? "new" : filterId}:${query.toString()}`}
      {...props}
      filterId={filterId}
      seed={seed}
      today={today}
    />
  );
}
function ReportFilterEditorBinding(
  props: Props & { filterId: string; seed: ReportFilterDefinition; today: string },
) {
  const id = useMemo(
    () => (props.creating ? props.nextIdentifier() : props.filterId),
    [props.creating, props.nextIdentifier, props.filterId],
  );
  const controller = useMemo(
    () =>
      new ReportFilterEditorController(
        props.actions,
        props.access,
        props.creating,
        id,
        props.nextIdentifier,
      ),
    [props.actions, props.access, props.creating, id, props.nextIdentifier],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useWorkspaceRevalidation(state.failure);
  const form = useReportFilterForm(
    controller,
    state,
    props.seed,
    props.access,
    props.companies,
    props.today,
    props.locale,
  );
  return (
    <ReportFilterEditorPage
      state={state}
      form={form}
      creating={props.creating}
      companyName={props.companyName}
      locale={props.locale}
      backTo={savedReportFiltersPath(props.access.companyId)}
      openTo={
        state.filter && canReadReportKind(props.access.permissions, state.filter.kind)
          ? openSavedReportFilter(state.filter)
          : null
      }
      onRetry={controller.retry}
      onReviewDelete={controller.reviewDelete}
      onCancelDelete={controller.cancelDelete}
      onConfirmDelete={controller.confirmDelete}
    />
  );
}
