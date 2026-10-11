import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess, CompanyMembership } from "../../../identity/domain/entities/session";
import { canReadOrganization } from "../../../organization/domain/policies/organization-unit-policy";
import type { LoadOrganizationUnits } from "../../../organization/domain/usecases/load-organization-units";
import { canTransferEmployment } from "../../domain/policies/employment-transfer-policy";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { EmploymentTransferController } from "../controllers/employment-transfer-controller";
import { useEmploymentTransferForm } from "../controllers/use-employment-transfer-form";
import { EmploymentTransferPage } from "../pages/employment-transfer-page";
import { EmployeeAssignmentPicker } from "./employee-assignment-picker";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  people: PeopleUseCases;
  companies: readonly CompanyMembership[];
  loadUnits: Pick<LoadOrganizationUnits, "execute">;
  companyName: string;
  locale: Locale;
  nextIdentifier: () => string;
};
export function EmploymentTransferScreen(props: Props) {
  const { employeeId = "" } = useParams();
  const [parameters] = useSearchParams();
  const company = new URLSearchParams({ company: props.access.companyId });
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/people/employees?${company}`} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={`/people/employees/${encodeURIComponent(employeeId)}/transfer?${company}`}
        replace
      />
    );
  return (
    <EmploymentTransferBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}`}
      {...props}
      employeeId={employeeId}
    />
  );
}
function EmploymentTransferBinding(props: Props & { employeeId: string }) {
  const controller = useMemo(
    () =>
      new EmploymentTransferController(
        props.people,
        props.access,
        props.employeeId,
        props.nextIdentifier,
      ),
    [props.people, props.access, props.employeeId, props.nextIdentifier],
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
  const form = useEmploymentTransferForm(controller, state);
  const targetAccess = useMemo<CompanyAccess | null>(
    () =>
      state.review
        ? Object.freeze({
            companyId: state.review.targetCompany.id,
            permissions: state.review.targetPermissions,
          })
        : null,
    [state.review],
  );
  const targets = useMemo(
    () => props.companies.filter((company) => company.id !== props.access.companyId),
    [props.companies, props.access.companyId],
  );
  const scopeFailure =
    state.failure?.code === "company_access_denied" &&
    typeof state.failure.parameters.companyId === "string" &&
    state.failure.parameters.companyId !== props.access.companyId
      ? null
      : state.failure;
  useWorkspaceRevalidation(scopeFailure);
  const query = new URLSearchParams({ company: props.access.companyId });
  if (state.review) query.set("asOf", state.review.sourceDate);
  const backTo = `/people/employees/${encodeURIComponent(props.employeeId)}?${query}`;
  const historyTo = state.receipt ? `${backTo}&tab=transfers` : null;
  return (
    <>
      <EmploymentTransferPage
        state={state}
        form={form}
        companyName={props.companyName}
        targets={targets}
        locale={props.locale}
        backTo={backTo}
        historyTo={historyTo}
        canChooseOrganization={
          targetAccess !== null && canReadOrganization(targetAccess.permissions)
        }
        canChooseManager={targetAccess?.permissions.includes("people.read") ?? false}
        canTransfer={canTransferEmployment(props.access.permissions)}
        onConfirm={controller.confirmTransfer}
        onDismiss={controller.dismissTransfer}
        onRetry={controller.retryTransfer}
      />
      {form.picker && form.editable && state.review && targetAccess && (
        <EmployeeAssignmentPicker
          key={`${targetAccess.companyId}:${form.picker}:${state.review.targetDate}`}
          accountId={props.accountId}
          access={targetAccess}
          kind={form.picker}
          effectiveDate={state.review.targetDate}
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
