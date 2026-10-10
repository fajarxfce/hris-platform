import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canBrowseEmployeeLeave } from "../../../leave/domain/policies/leave-read-policy";
import { canPrepareLifecycleCase } from "../../../lifecycle/domain/policies/lifecycle-case-start-policy";
import { canReadEmploymentHistory } from "../../domain/policies/employee-policy";
import { canManageEmployment } from "../../domain/policies/employment-policy";
import { canReadPersonProfile } from "../../domain/policies/person-profile-policy";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { EmployeeDetailsController } from "../controllers/employee-details-controller";
import {
  type EmployeeTab,
  employeeSearchFromParameters,
  employeeSearchParameters,
} from "../models/employee-route";
import { employeeDetailsView } from "../models/employee-view";
import { EmployeeDetailsPage } from "../pages/employee-details-page";
import { EmploymentHistoryBinding } from "./employment-history-binding";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  people: PeopleUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
};

export function EmployeeDetailsScreen(props: Props) {
  const { employeeId = "" } = useParams();
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = employeeSearchFromParameters(parameters, props.access.companyId, today);
  const backTo = `/people/employees?${employeeSearchParameters(search, props.access.companyId)}`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={backTo} replace />;
  if (!parameters.has("company") || !parameters.has("asOf")) {
    const canonical = new URLSearchParams(parameters);
    canonical.set("company", props.access.companyId);
    canonical.set("asOf", search.asOf);
    return (
      <Navigate to={`/people/employees/${encodeURIComponent(employeeId)}?${canonical}`} replace />
    );
  }
  const tab =
    canReadEmploymentHistory(props.access.permissions) && parameters.get("tab") === "history"
      ? "history"
      : "overview";
  return (
    <EmployeeDetailsBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${search.asOf}`}
      {...props}
      id={employeeId}
      asOf={search.asOf}
      today={today}
      backTo={backTo}
      balancesTo={
        canBrowseEmployeeLeave(props.access.permissions)
          ? `/leave/employees/${encodeURIComponent(employeeId)}/balances?${new URLSearchParams({ company: props.access.companyId, year: search.asOf.slice(0, 4) })}`
          : null
      }
      leaveTo={
        canBrowseEmployeeLeave(props.access.permissions)
          ? `/leave/requests?${new URLSearchParams({ company: props.access.companyId, employee: employeeId })}`
          : null
      }
      startLifecycleTo={
        canPrepareLifecycleCase(props.access.permissions)
          ? `/people/employees/${encodeURIComponent(employeeId)}/lifecycle/new?${new URLSearchParams({ company: props.access.companyId, asOf: search.asOf })}`
          : null
      }
      editEmploymentTo={
        canManageEmployment(props.access.permissions)
          ? `/people/employees/${encodeURIComponent(employeeId)}/employment/edit?${employeeSearchParameters(search, props.access.companyId)}`
          : null
      }
      profileTo={
        canReadPersonProfile(props.access.permissions)
          ? `/people/employees/${encodeURIComponent(employeeId)}/profile?${employeeSearchParameters(search, props.access.companyId)}`
          : null
      }
      tab={tab}
      after={parameters.get("historyAfter")}
      onTab={(selected) => {
        const next = new URLSearchParams(parameters);
        next.set("company", props.access.companyId);
        next.set("asOf", search.asOf);
        if (selected === "history") next.set("tab", "history");
        else next.delete("tab");
        setParameters(next);
      }}
      onHistoryPage={(after) => {
        const next = new URLSearchParams(parameters);
        if (after === null) next.delete("historyAfter");
        else next.set("historyAfter", after);
        setParameters(next);
      }}
    />
  );
}

function EmployeeDetailsBinding({
  access,
  people,
  companyName,
  locale,
  id,
  asOf,
  today,
  backTo,
  profileTo,
  editEmploymentTo,
  startLifecycleTo,
  leaveTo,
  balancesTo,
  tab,
  after,
  onTab,
  onHistoryPage,
}: Props & {
  id: string;
  asOf: string;
  today: string;
  backTo: string;
  profileTo: string | null;
  editEmploymentTo: string | null;
  startLifecycleTo: string | null;
  leaveTo: string | null;
  balancesTo: string | null;
  tab: EmployeeTab;
  after: string | null;
  onTab: (tab: string) => void;
  onHistoryPage: (after: string | null) => void;
}) {
  const controller = useMemo(
    () => new EmployeeDetailsController(people.loadEmployee, access, id, asOf),
    [people.loadEmployee, access, id, asOf],
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
  const view = useMemo(
    () => (state.employee ? employeeDetailsView(state.employee, asOf, locale) : null),
    [state.employee, asOf, locale],
  );
  return (
    <EmployeeDetailsPage
      state={state}
      view={view}
      companyName={companyName}
      locale={locale}
      backTo={backTo}
      profileTo={profileTo}
      editEmploymentTo={editEmploymentTo}
      startLifecycleTo={startLifecycleTo}
      leaveTo={leaveTo}
      balancesTo={balancesTo}
      tab={tab}
      canReadHistory={canReadEmploymentHistory(access.permissions)}
      onTab={onTab}
      onRefresh={controller.refresh}
      history={
        <EmploymentHistoryBinding
          access={access}
          loadHistory={people.loadEmploymentHistory}
          id={id}
          after={after}
          asOf={asOf}
          today={today}
          locale={locale}
          onPage={onHistoryPage}
          onScopeFailure={controller.reportScopeFailure}
        />
      }
    />
  );
}
