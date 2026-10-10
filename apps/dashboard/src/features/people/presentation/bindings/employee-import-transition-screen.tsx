import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeImportAction } from "../../domain/entities/employee-import-change";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { EmployeeImportTransitionController } from "../controllers/employee-import-transition-controller";
import { useEmployeeImportTransitionForm } from "../controllers/use-employee-import-transition-form";
import { employeeImportParameters } from "../models/employee-import-route";
import { employeeImportView } from "../models/employee-import-view";
import { EmployeeImportTransitionPage } from "../pages/employee-import-transition-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  people: PeopleUseCases;
  action: EmployeeImportAction;
  companyName: string;
  timezone: string;
  locale: Locale;
  nextIdentifier: () => string;
};
export function EmployeeImportTransitionScreen(props: Props) {
  const { importId = "" } = useParams();
  const [parameters] = useSearchParams();
  const query = employeeImportParameters(props.access.companyId, parameters.get("after"));
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`/people/imports?${employeeImportParameters(props.access.companyId, null)}`}
        replace
      />
    );
  const path = `/people/imports/${encodeURIComponent(importId)}`;
  if (!parameters.has("company"))
    return <Navigate to={`${path}/${props.action}?${query}`} replace />;
  return (
    <EmployeeImportTransitionBinding
      key={`${props.accountId}:${props.access.companyId}:${importId}:${props.action}`}
      {...props}
      id={importId}
      backTo={`${path}?${query}`}
    />
  );
}
function EmployeeImportTransitionBinding(props: Props & { id: string; backTo: string }) {
  const controller = useMemo(
    () =>
      new EmployeeImportTransitionController(
        props.people,
        props.access,
        props.id,
        props.action,
        props.nextIdentifier,
      ),
    [props.people, props.access, props.id, props.action, props.nextIdentifier],
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
  const form = useEmployeeImportTransitionForm(controller, state, props.access);
  useWorkspaceRevalidation(state.failure);
  const view = useMemo(
    () => (state.review ? employeeImportView(state.review, props.locale, props.timezone) : null),
    [state.review, props.locale, props.timezone],
  );
  return (
    <EmployeeImportTransitionPage
      state={state}
      form={form}
      view={view}
      action={props.action}
      companyName={props.companyName}
      locale={props.locale}
      backTo={props.backTo}
      onRetry={controller.retry}
    />
  );
}
