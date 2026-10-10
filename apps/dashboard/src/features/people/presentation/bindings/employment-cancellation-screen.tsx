import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { EmploymentCancellationController } from "../controllers/employment-cancellation-controller";
import { useEmploymentCancellationForm } from "../controllers/use-employment-cancellation-form";
import { employeeSearchFromParameters, employeeSearchParameters } from "../models/employee-route";
import { employmentRevisionView } from "../models/employment-history-view";
import { EmploymentCancellationPage } from "../pages/employment-cancellation-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  people: PeopleUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
  nextIdentifier: () => string;
};
export function EmploymentCancellationScreen(props: Props) {
  const { employeeId = "", revision = "" } = useParams();
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = employeeSearchFromParameters(parameters, props.access.companyId, today);
  const canonical = employeeSearchParameters(search, props.access.companyId);
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/people/employees?${canonical}`} replace />;
  const employeePath = `/people/employees/${encodeURIComponent(employeeId)}`;
  if (!parameters.has("company") || !parameters.has("asOf"))
    return (
      <Navigate
        to={`${employeePath}/revisions/${encodeURIComponent(revision)}/cancel?${canonical}`}
        replace
      />
    );
  return (
    <EmploymentCancellationBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${revision}:${search.asOf}`}
      {...props}
      employeeId={employeeId}
      revision={revision}
      backTo={`${employeePath}?${canonical}&tab=history`}
    />
  );
}
function EmploymentCancellationBinding(
  props: Props & { employeeId: string; revision: string; backTo: string },
) {
  const controller = useMemo(
    () =>
      new EmploymentCancellationController(
        props.people,
        props.access,
        props.employeeId,
        props.revision,
        props.nextIdentifier,
      ),
    [props.people, props.access, props.employeeId, props.revision, props.nextIdentifier],
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
  const form = useEmploymentCancellationForm(controller, state);
  useWorkspaceRevalidation(state.failure);
  const revision = useMemo(
    () => (state.details ? employmentRevisionView(state.details.revision, props.locale) : null),
    [state.details, props.locale],
  );
  return (
    <EmploymentCancellationPage
      state={state}
      form={form}
      revision={revision}
      companyName={props.companyName}
      locale={props.locale}
      backTo={props.backTo}
      onRetry={controller.retry}
    />
  );
}
