import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeavePolicyQuery } from "../../domain/entities/leave-policy-definition";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeavePoliciesController } from "../controllers/leave-policies-controller";
import { useLeavePolicyFilters } from "../controllers/use-leave-policy-filters";
import { leavePolicyParameters, leavePolicyQuery } from "../models/leave-policy-route";
import { leavePoliciesView } from "../models/leave-policy-view";
import { LeavePoliciesPage } from "../pages/leave-policies-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  leave: LeaveUseCases;
  companyName: string;
  locale: Locale;
};
export function LeavePoliciesScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const query = useMemo(
    () => leavePolicyQuery(parameters, props.access.companyId),
    [parameters, props.access.companyId],
  );
  const canonical = leavePolicyParameters(props.access.companyId, query).toString();
  if (parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/leave/policies?${canonical}`} replace />;
  return (
    <LeavePoliciesBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      query={query}
      onQuery={(value) => setParameters(leavePolicyParameters(props.access.companyId, value))}
      onOpen={(id) => navigate(`/leave/policies/${encodeURIComponent(id)}?${canonical}`)}
    />
  );
}
function LeavePoliciesBinding({
  access,
  leave,
  companyName,
  locale,
  query,
  onQuery,
  onOpen,
}: Props & {
  query: LeavePolicyQuery;
  onQuery: (query: LeavePolicyQuery) => void;
  onOpen: (id: string) => void;
}) {
  const controller = useMemo(
    () => new LeavePoliciesController(leave.loadPolicies, access, query),
    [leave.loadPolicies, access, query],
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
  const filters = useLeavePolicyFilters(query, onQuery);
  const rows = useMemo(
    () => (state.page ? leavePoliciesView(state.page, locale) : []),
    [state.page, locale],
  );
  return (
    <LeavePoliciesPage
      state={state}
      rows={rows}
      companyName={companyName}
      locale={locale}
      filters={filters}
      firstPage={query.after === null}
      onRefresh={controller.refresh}
      onFirst={() => onQuery({ ...query, after: null })}
      onNext={() => {
        if (state.page?.nextCursor) onQuery({ ...query, after: state.page.nextCursor });
      }}
      onOpen={onOpen}
    />
  );
}
