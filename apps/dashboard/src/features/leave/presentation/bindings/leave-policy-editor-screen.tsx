import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeavePolicyEditorController } from "../controllers/leave-policy-editor-controller";
import { useLeavePolicyForm } from "../controllers/use-leave-policy-form";
import { leavePolicyParameters, leavePolicyQuery } from "../models/leave-policy-route";
import { LeavePolicyEditorPage } from "../pages/leave-policy-editor-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  leave: LeaveUseCases;
  companyName: string;
  locale: Locale;
  timezone: string;
  creating: boolean;
  nextIdentifier: () => string;
};
export function LeavePolicyEditorScreen(props: Props) {
  const { policyId = "" } = useParams();
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const directory = leavePolicyParameters(
    props.access.companyId,
    leavePolicyQuery(parameters, props.access.companyId),
  ).toString();
  const path = props.creating
    ? "/leave/policies/new"
    : `/leave/policies/${encodeURIComponent(policyId)}/edit`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/leave/policies?${directory}`} replace />;
  if (!parameters.has("company")) return <Navigate to={`${path}?${directory}`} replace />;
  return (
    <LeavePolicyEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${path}:${directory}`}
      {...props}
      policyId={policyId}
      parameters={directory}
      today={today}
    />
  );
}
function LeavePolicyEditorBinding(
  props: Props & { policyId: string; parameters: string; today: string },
) {
  const { creating, nextIdentifier, policyId, leave, access } = props;
  const id = useMemo(
    () => (creating ? nextIdentifier() : policyId),
    [creating, nextIdentifier, policyId],
  );
  const controller = useMemo(
    () => new LeavePolicyEditorController(leave, access, creating, id, nextIdentifier),
    [leave, access, creating, id, nextIdentifier],
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
  const form = useLeavePolicyForm(controller, state, props.today);
  useWorkspaceRevalidation(state.failure);
  return (
    <LeavePolicyEditorPage
      state={state}
      form={form}
      creating={creating}
      companyName={props.companyName}
      locale={props.locale}
      backTo={`/leave/policies?${props.parameters}`}
      savedTo={state.receipt ? `/leave/policies/${state.receipt.id}?${props.parameters}` : null}
      onRetry={controller.retrySave}
    />
  );
}
