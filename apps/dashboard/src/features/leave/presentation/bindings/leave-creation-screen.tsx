import { useEffect, useId, useMemo, useSyncExternalStore } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canPrepareLeave } from "../../domain/policies/leave-preparation-policy";
import type { LeaveCreationProps } from "../contracts/leave-creation-props";
import { LeaveCreationController } from "../controllers/leave-creation-controller";
import { useLeaveCreationForm } from "../controllers/use-leave-creation-form";
import { leaveCreationReviewView } from "../models/leave-creation-view";
import { leaveParameters, leaveQuery } from "../models/leave-route";
import { LeaveCreationPage } from "../pages/leave-creation-page";
import {
  LeaveEmployeePicker,
  LeaveEvidencePicker,
  LeaveTypePicker,
} from "./leave-preparation-pickers";

export function LeaveCreationScreen(props: LeaveCreationProps) {
  const [parameters] = useSearchParams();
  const query = leaveParameters(
    props.access.companyId,
    leaveQuery(parameters, props.access.companyId),
  ).toString();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/leave/requests?${query}`} replace />;
  if (!parameters.has("company")) return <Navigate to={`/leave/requests/new?${query}`} replace />;
  return (
    <LeaveCreationBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      query={query}
    />
  );
}

function LeaveCreationBinding(props: LeaveCreationProps & { query: string }) {
  const controller = useMemo(
    () => new LeaveCreationController(props.leave, props.access, props.nextIdentifier),
    [props.leave, props.access, props.nextIdentifier],
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
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const form = useLeaveCreationForm(
    controller,
    state,
    today,
    canPrepareLeave(props.access.permissions),
  );
  const view = useMemo(
    () => (state.review ? leaveCreationReviewView(state.review, props.locale) : null),
    [state.review, props.locale],
  );
  const formId = useId();
  return (
    <LeaveCreationPage
      formId={formId}
      state={state}
      form={form}
      view={view}
      locale={props.locale}
      companyName={props.companyName}
      backTo={`/leave/requests?${props.query}`}
      savedTo={
        state.receipt
          ? `/leave/requests/${encodeURIComponent(state.receipt.id)}?${props.query}`
          : null
      }
      onRetry={controller.retry}
      onRemoveEvidence={controller.removeEvidence}
      picker={
        form.picker === "employee" && form.canChooseEmployee ? (
          <LeaveEmployeePicker
            {...props}
            controller={controller}
            onSelect={form.selectEmployee}
            onClose={form.closePicker}
          />
        ) : form.picker === "type" && form.canChooseType ? (
          <LeaveTypePicker
            key={form.from.value}
            {...props}
            controller={controller}
            asOf={form.from.value}
            onSelect={form.selectType}
            onClose={form.closePicker}
          />
        ) : form.picker === "evidence" && state.employee && form.editable ? (
          <LeaveEvidencePicker
            key={state.employee.id}
            {...props}
            controller={controller}
            employeeId={state.employee.id}
            selected={state.evidence}
            onSelect={controller.addEvidence}
            onClose={form.closePicker}
          />
        ) : null
      }
    />
  );
}
