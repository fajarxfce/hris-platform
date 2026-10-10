import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadOrganization } from "../../../organization/domain/policies/organization-unit-policy";
import type { LoadOrganizationUnits } from "../../../organization/domain/usecases/load-organization-units";
import { canReadEmployees } from "../../domain/policies/employee-policy";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { EmployeeCreationController } from "../controllers/employee-creation-controller";
import { useEmployeeAssignmentPicker } from "../controllers/use-employee-assignment-picker";
import { useEmployeeCreationForm } from "../controllers/use-employee-creation-form";
import type {
  EmployeeAssignmentKind,
  EmployeeAssignmentOption,
} from "../models/employee-assignment";
import { EmployeeAssignmentPickerPage } from "../pages/employee-assignment-picker-page";
import { EmployeeCreationPage } from "../pages/employee-creation-page";

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
export function EmployeeCreationScreen(props: Props) {
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const backTo = canReadEmployees(props.access.permissions)
    ? `/people/employees?${new URLSearchParams({ company: props.access.companyId, asOf: today })}`
    : "/";
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={backTo} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={`/people/employees/new?${new URLSearchParams({ company: props.access.companyId })}`}
        replace
      />
    );
  return (
    <EmployeeCreationBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      today={today}
      backTo={backTo}
    />
  );
}

function EmployeeCreationBinding(props: Props & { today: string; backTo: string }) {
  const controller = useMemo(
    () =>
      new EmployeeCreationController(
        props.people.createEmployee,
        props.access,
        props.nextIdentifier,
      ),
    [props.people.createEmployee, props.access, props.nextIdentifier],
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
  const form = useEmployeeCreationForm(controller, state, props.today);
  useWorkspaceRevalidation(state.failure);
  const detailTo =
    state.receipt && state.startDate && canReadEmployees(props.access.permissions)
      ? `/people/employees/${encodeURIComponent(state.receipt.id)}?${new URLSearchParams({ company: props.access.companyId, asOf: state.startDate > props.today ? state.startDate : props.today })}`
      : null;
  return (
    <>
      <EmployeeCreationPage
        state={state}
        form={form}
        companyName={props.companyName}
        locale={props.locale}
        backTo={props.backTo}
        detailTo={detailTo}
        canChooseOrganization={canReadOrganization(props.access.permissions)}
        canChooseManager={canReadEmployees(props.access.permissions)}
        onRetry={controller.retrySave}
      />
      {form.picker && form.editable && (
        <EmployeeAssignmentPickerBinding
          key={`${form.picker}:${form.startDate.value}`}
          {...props}
          kind={form.picker}
          startDate={form.startDate.value}
          onSelect={form.select}
          onClose={form.closePicker}
        />
      )}
    </>
  );
}

function EmployeeAssignmentPickerBinding(
  props: Props & {
    kind: EmployeeAssignmentKind;
    startDate: string;
    onSelect: (option: EmployeeAssignmentOption) => void;
    onClose: () => void;
  },
) {
  const picker = useEmployeeAssignmentPicker(
    props.accountId,
    props.access,
    props.kind,
    props.startDate,
    props.loadUnits,
    props.people.loadEmployees,
  );
  useWorkspaceRevalidation(picker.failure);
  return (
    <EmployeeAssignmentPickerPage
      kind={props.kind}
      picker={picker}
      locale={props.locale}
      onClose={props.onClose}
      onSelect={(id) => {
        const option = picker.options.find((item) => item.id === id);
        if (option) props.onSelect(option);
      }}
    />
  );
}
