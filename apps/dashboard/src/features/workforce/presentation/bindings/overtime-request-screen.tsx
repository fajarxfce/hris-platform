import { useMemo } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canBrowseCompanyOvertime } from "../../domain/policies/overtime-read-policy";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { useOvertimeRequest } from "../controllers/use-overtime-request";
import { overtimeMessages } from "../i18n/overtime-messages";
import { overtimeActionLinks } from "../models/overtime-action-view";
import {
  overtimeParameters,
  overtimePath,
  overtimeRequestPath,
  overtimeSearchFromRoute,
} from "../models/overtime-route";
import { overtimeDetailsView } from "../models/overtime-view";
import { OvertimeRequestPage } from "../pages/overtime-request-page";

export function OvertimeRequestScreen(props: WorkforceScreenProps) {
  const { overtimeId = "" } = useParams();
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = useMemo(
    () => overtimeSearchFromRoute(parameters, props.access.companyId, today),
    [parameters, props.access.companyId, today],
  );
  const query = overtimeParameters(props.access.companyId, search);
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`${overtimePath}?${query}`} replace />;
  if (!parameters.has("company")) {
    if (parameters.has("historyAfter"))
      query.set("historyAfter", parameters.get("historyAfter") ?? "");
    return <Navigate to={`${overtimeRequestPath(overtimeId)}?${query}`} replace />;
  }
  return (
    <OvertimeRequestBinding
      key={`${props.accountId}:${props.access.companyId}:${overtimeId}`}
      {...props}
      id={overtimeId}
      backQuery={query.toString()}
    />
  );
}

function OvertimeRequestBinding(props: WorkforceScreenProps & { id: string; backQuery: string }) {
  const [parameters, setParameters] = useSearchParams(),
    after = parameters.get("historyAfter");
  const state = useOvertimeRequest(
    props.accountId,
    props.access,
    props.id,
    after,
    props.workforce.loadOvertimeRequest,
  );
  const selected = parameters.get("change");
  const view = useMemo(
    () => (state.details ? overtimeDetailsView(state.details, selected, props.locale) : null),
    [state.details, selected, props.locale],
  );
  const directory = canBrowseCompanyOvertime(props.access.permissions),
    text = overtimeMessages(props.locale);
  useWorkspaceRevalidation(state.failure);
  return (
    <OvertimeRequestPage
      state={state}
      view={view}
      companyName={props.companyName}
      locale={props.locale}
      actions={overtimeActionLinks(state.details, props.access, props.backQuery, props.locale)}
      backTo={
        directory
          ? `${overtimePath}?${props.backQuery}`
          : `/approvals?${new URLSearchParams({ company: props.access.companyId })}`
      }
      backLabel={directory ? text.back : text.backApprovals}
      approvalTo={
        state.details?.approval
          ? `/approvals/${state.details.approval.id}?${new URLSearchParams({ company: props.access.companyId })}`
          : null
      }
      firstHistory={after === null}
      onLatest={() => {
        const next = new URLSearchParams(parameters);
        next.delete("historyAfter");
        next.delete("change");
        setParameters(next);
      }}
      onOlder={() => {
        if (state.details?.history.nextCursor) {
          const next = new URLSearchParams(parameters);
          next.set("historyAfter", state.details.history.nextCursor);
          next.delete("change");
          setParameters(next);
        }
      }}
      onOpenChange={(revision) => {
        if (state.details?.history.items.some((item) => String(item.revision) === revision)) {
          const next = new URLSearchParams(parameters);
          next.set("change", revision);
          setParameters(next, { replace: true });
        }
      }}
      onCloseChange={() => {
        const next = new URLSearchParams(parameters);
        next.delete("change");
        setParameters(next, { replace: true });
      }}
    />
  );
}
