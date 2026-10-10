import { useMemo } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess, CompanyMembership } from "../../../identity/domain/entities/session";
import { canReadEmployees } from "../../domain/policies/employee-policy";
import { canLinkPersonAccount } from "../../domain/policies/person-account-binding-policy";
import {
  canManagePersonProfile,
  canReadProfileHistory,
} from "../../domain/policies/person-profile-policy";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { usePersonProfile } from "../controllers/use-person-profile";
import { usePersonProfileForm } from "../controllers/use-person-profile-form";
import { employeeSearchFromParameters, employeeSearchParameters } from "../models/employee-route";
import { personProfileView } from "../models/person-profile-view";
import { PersonProfileEditorPage } from "../pages/person-profile-editor-page";
import { PersonProfilePage } from "../pages/person-profile-page";
import { PersonProfileHistoryBinding } from "./person-profile-history-binding";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  people: PeopleUseCases;
  companies: readonly CompanyMembership[];
  companyName: string;
  timezone: string;
  locale: Locale;
  mode: "read" | "edit";
  nextIdentifier: () => string;
};
export function PersonProfileScreen(props: Props) {
  const { employeeId = "" } = useParams();
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = employeeSearchFromParameters(parameters, props.access.companyId, today);
  const employeeParameters = employeeSearchParameters(search, props.access.companyId);
  const directoryTo = canReadEmployees(props.access.permissions)
    ? `/people/employees?${employeeParameters}`
    : "/";
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={directoryTo} replace />;
  const path = `/people/employees/${encodeURIComponent(employeeId)}/profile`;
  if (!parameters.has("company")) {
    const canonical = new URLSearchParams(parameters);
    canonical.set("company", props.access.companyId);
    return <Navigate to={`${path}${props.mode === "edit" ? "/edit" : ""}?${canonical}`} replace />;
  }
  const profileTo = `${path}?${employeeParameters}`;
  const key = `${props.accountId}:${props.access.companyId}:${employeeId}:${props.mode}`;
  if (props.mode === "edit")
    return (
      <PersonProfileEditorBinding key={key} {...props} employeeId={employeeId} backTo={profileTo} />
    );
  return (
    <PersonProfileDetailsBinding
      key={key}
      {...props}
      employeeId={employeeId}
      backTo={
        canReadEmployees(props.access.permissions)
          ? `/people/employees/${encodeURIComponent(employeeId)}?${employeeParameters}`
          : "/"
      }
      editTo={`${path}/edit?${employeeParameters}`}
      tab={
        canReadProfileHistory(props.access.permissions) && parameters.get("tab") === "history"
          ? "history"
          : "overview"
      }
      after={parameters.get("profileAfter")}
      onTab={(tab) => {
        const next = new URLSearchParams(parameters);
        if (tab === "history") next.set("tab", "history");
        else next.delete("tab");
        setParameters(next);
      }}
      onPage={(after) => {
        const next = new URLSearchParams(parameters);
        if (after === null) next.delete("profileAfter");
        else next.set("profileAfter", after);
        setParameters(next);
      }}
    />
  );
}

function PersonProfileDetailsBinding(
  props: Props & {
    employeeId: string;
    backTo: string;
    editTo: string;
    tab: "overview" | "history";
    after: string | null;
    onTab: (tab: string) => void;
    onPage: (after: string | null) => void;
  },
) {
  const { state, controller } = usePersonProfile(
    props.people,
    props.access,
    props.employeeId,
    "read",
    props.nextIdentifier,
  );
  useWorkspaceRevalidation(state.failure);
  const view = useMemo(
    () =>
      state.profile
        ? personProfileView(
            state.profile,
            props.companies.find((company) => company.id === state.profile?.ownerCompanyId)?.name ??
              null,
            props.locale,
          )
        : null,
    [state.profile, props.companies, props.locale],
  );
  return (
    <PersonProfilePage
      state={state}
      view={view}
      locale={props.locale}
      companyName={props.companyName}
      backTo={props.backTo}
      editTo={
        state.profile && canManagePersonProfile(props.access, state.profile) ? props.editTo : null
      }
      linkAccountTo={
        state.profile && canLinkPersonAccount(props.access, state.profile)
          ? `/people/employees/${encodeURIComponent(props.employeeId)}/account-link?company=${props.access.companyId}`
          : null
      }
      owned={state.profile?.ownerCompanyId === props.access.companyId}
      tab={props.tab}
      onTab={props.onTab}
      canReadHistory={canReadProfileHistory(props.access.permissions)}
      onRefresh={controller.refresh}
      history={
        <PersonProfileHistoryBinding
          access={props.access}
          loadHistory={props.people.loadPersonProfileHistory}
          id={props.employeeId}
          after={props.after}
          locale={props.locale}
          onPage={props.onPage}
          onScopeFailure={controller.reportScopeFailure}
        />
      }
    />
  );
}
function PersonProfileEditorBinding(props: Props & { employeeId: string; backTo: string }) {
  const { state, controller } = usePersonProfile(
    props.people,
    props.access,
    props.employeeId,
    "edit",
    props.nextIdentifier,
  );
  const form = usePersonProfileForm(controller, state);
  useWorkspaceRevalidation(state.failure);
  return (
    <PersonProfileEditorPage
      state={state}
      form={form}
      companyName={props.companyName}
      locale={props.locale}
      backTo={props.backTo}
      onRetrySave={controller.retrySave}
    />
  );
}
