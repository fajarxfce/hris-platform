import { useEffect, useMemo, useRef, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeavePolicyController } from "../controllers/leave-policy-controller";
import { leavePolicyParameters, leavePolicyQuery } from "../models/leave-policy-route";
import { leavePolicyRevisionView, leavePolicyView } from "../models/leave-policy-view";
import { LeavePolicyPage } from "../pages/leave-policy-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  leave: LeaveUseCases;
  companyName: string;
  locale: Locale;
  timezone: string;
};
export function LeavePolicyScreen(props: Props) {
  const { policyId = "" } = useParams();
  const [parameters, setParameters] = useSearchParams();
  const directory = leavePolicyParameters(
    props.access.companyId,
    leavePolicyQuery(parameters, props.access.companyId),
  );
  const backTo = `/leave/policies?${directory}`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={backTo} replace />;
  const historyAfter = parameters.get("historyAfter");
  if (!parameters.has("company")) {
    if (historyAfter !== null) directory.set("historyAfter", historyAfter);
    return <Navigate to={`/leave/policies/${encodeURIComponent(policyId)}?${directory}`} replace />;
  }
  return (
    <LeavePolicyBinding
      key={`${props.accountId}:${props.access.companyId}:${policyId}`}
      {...props}
      id={policyId}
      historyAfter={historyAfter}
      backTo={backTo}
      editTo={`/leave/policies/${encodeURIComponent(policyId)}/edit?${directory}`}
      onHistory={(after) => {
        const next = new URLSearchParams(directory);
        if (after !== null) next.set("historyAfter", after);
        setParameters(next);
      }}
    />
  );
}
function LeavePolicyBinding({
  leave,
  access,
  id,
  historyAfter,
  locale,
  timezone,
  companyName,
  backTo,
  editTo,
  onHistory,
}: Props & {
  id: string;
  historyAfter: string | null;
  backTo: string;
  editTo: string;
  onHistory: (after: string | null) => void;
}) {
  const controller = useMemo(
    () => new LeavePolicyController(leave.loadPolicy, access, id, historyAfter),
    [leave.loadPolicy, access, id, historyAfter],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  const revisionRef = useRef<HTMLElement>(null);
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useEffect(() => {
    if (state.selectedRevision) revisionRef.current?.focus();
  }, [state.selectedRevision]);
  useWorkspaceRevalidation(state.failure);
  const view = useMemo(
    () => (state.review ? leavePolicyView(state.review, locale, timezone) : null),
    [state.review, locale, timezone],
  );
  const selected = useMemo(
    () =>
      state.selectedRevision
        ? leavePolicyRevisionView(state.selectedRevision, locale, timezone)
        : null,
    [state.selectedRevision, locale, timezone],
  );
  return (
    <LeavePolicyPage
      state={state}
      view={view}
      selected={selected}
      revisionRef={revisionRef}
      companyName={companyName}
      locale={locale}
      timezone={timezone}
      backTo={backTo}
      firstHistory={historyAfter === null}
      editTo={state.review ? editTo : null}
      onRefresh={controller.refresh}
      onLatest={() => onHistory(null)}
      onOlder={() => {
        if (state.review?.history.nextCursor) onHistory(state.review.history.nextCursor);
      }}
      onRevision={controller.selectRevision}
    />
  );
}
