import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LoadCompanyMembers } from "../../../identity/domain/usecases/load-company-members";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { PersonAccountBindingController } from "../controllers/person-account-binding-controller";
import { usePersonAccountBindingForm } from "../controllers/use-person-account-binding-form";
import { PersonAccountBindingPage } from "../pages/person-account-binding-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  people: PeopleUseCases;
  loadMembers: Pick<LoadCompanyMembers, "execute">;
  companyName: string;
  locale: Locale;
  nextIdentifier: () => string;
};
export function PersonAccountBindingScreen(props: Props) {
  const { employeeId = "" } = useParams();
  const [parameters] = useSearchParams();
  const company = parameters.get("company");
  if (company !== null && company !== props.access.companyId)
    return <Navigate to={`/people/employees?company=${props.access.companyId}`} replace />;
  if (company === null)
    return (
      <Navigate
        to={`/people/employees/${encodeURIComponent(employeeId)}/account-link?company=${props.access.companyId}`}
        replace
      />
    );
  return (
    <PersonAccountBindingScreenContent
      key={`${props.accountId}:${props.access.companyId}:${employeeId}`}
      {...props}
      employeeId={employeeId}
    />
  );
}
function PersonAccountBindingScreenContent(props: Props & { employeeId: string }) {
  const controller = useMemo(
    () =>
      new PersonAccountBindingController(
        props.people,
        props.loadMembers,
        props.access,
        props.accountId,
        props.employeeId,
        props.nextIdentifier,
      ),
    [
      props.people,
      props.loadMembers,
      props.access,
      props.accountId,
      props.employeeId,
      props.nextIdentifier,
    ],
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
  const form = usePersonAccountBindingForm(controller, state);
  const rows = useMemo(
    () =>
      state.candidates.map((member) => ({
        id: member.id,
        actionLabel: member.displayName,
        cells: [member.displayName, member.email],
      })),
    [state.candidates],
  );
  return (
    <PersonAccountBindingPage
      state={state}
      form={form}
      rows={rows}
      locale={props.locale}
      companyName={props.companyName}
      backTo={`/people/employees/${encodeURIComponent(props.employeeId)}/profile?company=${props.access.companyId}`}
      onSelect={controller.selectAccount}
      onFirst={() => {
        void controller.loadCandidates(null);
      }}
      onNext={() => {
        if (state.candidateNext) void controller.loadCandidates(state.candidateNext);
      }}
      onRetry={controller.retry}
    />
  );
}
