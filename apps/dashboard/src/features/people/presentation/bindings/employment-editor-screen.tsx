import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadOrganization } from "../../../organization/domain/policies/organization-unit-policy";
import type { LoadOrganizationUnits } from "../../../organization/domain/usecases/load-organization-units";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { EmploymentEditorController } from "../controllers/employment-editor-controller";
import { useEmploymentEditorForm } from "../controllers/use-employment-editor-form";
import { employeeSearchFromParameters, employeeSearchParameters } from "../models/employee-route";
import { EmploymentEditorPage } from "../pages/employment-editor-page";
import { EmployeeAssignmentPicker } from "./employee-assignment-picker";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  people: PeopleUseCases;
  loadUnits: Pick<LoadOrganizationUnits, "execute">;
  companyName: string;
  timezone: string;
  locale: Locale;
  nextIdentifier: () => string;
};
export function EmploymentEditorScreen(props: Props) {
  const { employeeId = "" } = useParams();
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = employeeSearchFromParameters(parameters, props.access.companyId, today);
  const canonical = employeeSearchParameters(search, props.access.companyId);
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/people/employees?${canonical}`} replace />;
  const path = `/people/employees/${encodeURIComponent(employeeId)}`;
  if (!parameters.has("company") || !parameters.has("asOf"))
    return <Navigate to={`${path}/employment/edit?${canonical}`} replace />;
  return (
    <EmploymentEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${search.asOf}`}
      {...props}
      employeeId={employeeId}
      asOf={search.asOf}
      backTo={`${path}?${canonical}`}
    />
  );
}
function EmploymentEditorBinding(
  props: Props & { employeeId: string; asOf: string; backTo: string },
) {
  const controller = useMemo(
    () =>
      new EmploymentEditorController(
        props.people,
        props.access,
        props.employeeId,
        props.asOf,
        props.nextIdentifier,
      ),
    [props.people, props.access, props.employeeId, props.asOf, props.nextIdentifier],
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
  const form = useEmploymentEditorForm(controller, state);
  useWorkspaceRevalidation(state.failure);
  const detailTo =
    state.receipt && state.savedDate
      ? `/people/employees/${encodeURIComponent(state.receipt.id)}?${new URLSearchParams({ company: props.access.companyId, asOf: state.savedDate })}`
      : null;
  return (
    <>
      <EmploymentEditorPage
        state={state}
        form={form}
        companyName={props.companyName}
        locale={props.locale}
        backTo={props.backTo}
        detailTo={detailTo}
        canChooseOrganization={canReadOrganization(props.access.permissions)}
        onRetry={controller.retrySave}
      />
      {form.picker && form.editable && (
        <EmployeeAssignmentPicker
          key={`${form.picker}:${form.effectiveFrom.value}`}
          accountId={props.accountId}
          access={props.access}
          kind={form.picker}
          effectiveDate={form.effectiveFrom.value}
          excludedEmployeeId={props.employeeId}
          loadUnits={props.loadUnits}
          loadEmployees={props.people.loadEmployees}
          locale={props.locale}
          onSelect={form.select}
          onClose={form.closePicker}
        />
      )}
    </>
  );
}
