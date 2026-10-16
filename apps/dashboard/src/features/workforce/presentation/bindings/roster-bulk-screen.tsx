import { useEffect, useId, useMemo, useSyncExternalStore } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { RosterBulkController } from "../controllers/roster-bulk-controller";
import { useRosterBulkForm } from "../controllers/use-roster-bulk-form";
import {
  calendarsPath,
  rosterBulkParameters,
  rosterBulkPath,
  workforceEmployeeParameters,
} from "../models/calendar-route";
import { rosterBulkFailureContext, rosterBulkView } from "../models/roster-bulk-view";
import { RosterBulkPage } from "../pages/roster-bulk-page";
import { RosterBulkEmployees } from "./roster-bulk-employees";
import { RosterBulkShifts } from "./roster-bulk-shifts";

export function RosterBulkScreen(props: WorkforceScreenProps) {
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const requested = parameters.get("from") ?? today;
  const from = isCalendarDate(requested) && requested >= "1900-01-01" ? requested : today;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={calendarsPath} replace />;
  if (!parameters.has("company") || parameters.get("from") !== from)
    return (
      <Navigate
        to={`${rosterBulkPath}?${rosterBulkParameters(props.access.companyId, from)}`}
        replace
      />
    );
  return (
    <RosterBulkBinding
      key={`${props.accountId}:${props.access.companyId}:${from}`}
      {...props}
      from={from}
    />
  );
}

function RosterBulkBinding(props: WorkforceScreenProps & { from: string }) {
  const { workforce, access, nextIdentifier } = props;
  const formId = useId();
  const controller = useMemo(
    () => new RosterBulkController(workforce, access, nextIdentifier),
    [workforce, access, nextIdentifier],
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
  const form = useRosterBulkForm(controller, state, access.companyId, props.from);
  const view = useMemo(
    () => (state.review ? rosterBulkView(state.review, props.locale) : null),
    [state.review, props.locale],
  );
  const failureContext = useMemo(
    () => rosterBulkFailureContext(state.failure, form.employees, props.locale),
    [state.failure, form.employees, props.locale],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <RosterBulkPage
      state={state}
      form={form}
      view={view}
      failureContext={failureContext}
      formId={formId}
      locale={props.locale}
      companyName={props.companyName}
      backTo={`${calendarsPath}?${workforceEmployeeParameters(access.companyId, { asOf: props.from, query: "", after: null })}`}
      shiftPicker={
        state.stage === "editing" && form.mode.value === "SHIFT" ? (
          <RosterBulkShifts {...props} form={form} />
        ) : null
      }
      employeePicker={
        state.stage === "editing" && form.lookupDate ? (
          <RosterBulkEmployees
            key={form.lookupDate}
            {...props}
            asOf={form.lookupDate}
            form={form}
          />
        ) : null
      }
      onRetry={controller.retry}
    />
  );
}
