import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { OvertimePlanController } from "../controllers/overtime-plan-controller";
import { useOvertimePlanForm } from "../controllers/use-overtime-plan-form";
import { overtimePlanningView } from "../models/overtime-planning-view";
import {
  overtimeParameters,
  overtimePath,
  overtimeRequestPath,
  overtimeSearchFromRoute,
} from "../models/overtime-route";
import { OvertimePlanPage } from "../pages/overtime-plan-page";
import { OvertimePlanningEmployeePicker } from "./overtime-planning-employee-picker";

export function OvertimePlanScreen(props: WorkforceScreenProps) {
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const query = overtimeParameters(
    props.access.companyId,
    overtimeSearchFromRoute(parameters, props.access.companyId, today),
  ).toString();
  if (parameters.get("company") !== props.access.companyId)
    return <Navigate to={`${overtimePath}/new?${query}`} replace />;
  return (
    <OvertimePlanBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      today={today}
      query={query}
    />
  );
}

function OvertimePlanBinding(props: WorkforceScreenProps & { today: string; query: string }) {
  const { workforce, access, nextIdentifier, today, locale } = props;
  const controller = useMemo(
    () => new OvertimePlanController(workforce, access, today, nextIdentifier),
    [workforce, access, today, nextIdentifier],
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
  const form = useOvertimePlanForm(
    controller,
    state,
    access.permissions.includes("overtime.manage"),
  );
  const view = useMemo(
    () => (state.review ? overtimePlanningView(state.review, locale) : null),
    [state.review, locale],
  );
  return (
    <>
      <OvertimePlanPage
        state={state}
        form={form}
        view={view}
        locale={locale}
        companyName={props.companyName}
        backTo={`${overtimePath}?${props.query}`}
        savedTo={state.receipt ? `${overtimeRequestPath(state.receipt.id)}?${props.query}` : null}
        onRetry={controller.retry}
      />
      {form.pickerOpen && form.canChooseEmployee && (
        <OvertimePlanningEmployeePicker
          key={state.workDate}
          {...props}
          workDate={state.workDate}
          onSelect={form.selectEmployee}
          onClose={form.closePicker}
        />
      )}
    </>
  );
}
