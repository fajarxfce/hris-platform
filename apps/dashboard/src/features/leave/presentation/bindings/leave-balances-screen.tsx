import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveBalanceQuery } from "../../domain/entities/leave-balance-query";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveBalancesController } from "../controllers/leave-balances-controller";
import { useLeaveBalanceFilters } from "../controllers/use-leave-balance-filters";
import { balanceParameters, balanceQuery } from "../models/leave-balance-route";
import { leaveBalanceEmployeeView, leaveBalancesView } from "../models/leave-balance-view";
import { LeaveBalancesPage } from "../pages/leave-balances-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  leave: LeaveUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
};
export function LeaveBalancesScreen(props: Props) {
  const { employeeId = "" } = useParams();
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const defaultYear = useMemo(
    () => companyDate(props.timezone, new Date()).slice(0, 4),
    [props.timezone],
  );
  const query = useMemo(() => balanceQuery(parameters, defaultYear), [parameters, defaultYear]);
  const path = `/leave/employees/${encodeURIComponent(employeeId)}/balances`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to="/" replace />;
  if (!parameters.has("company") || !parameters.has("year"))
    return <Navigate to={`${path}?${balanceParameters(props.access.companyId, query)}`} replace />;
  return (
    <LeaveBalancesBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}`}
      {...props}
      employeeId={employeeId}
      query={query}
      onQuery={(value) => setParameters(balanceParameters(props.access.companyId, value))}
      onOpen={(id) =>
        navigate(
          `${path}/${encodeURIComponent(id)}?${balanceParameters(props.access.companyId, { year: query.year, after: null }, query.after)}`,
        )
      }
    />
  );
}
function LeaveBalancesBinding({
  access,
  leave,
  companyName,
  locale,
  employeeId,
  query,
  onQuery,
  onOpen,
}: Props & {
  employeeId: string;
  query: LeaveBalanceQuery;
  onQuery: (query: LeaveBalanceQuery) => void;
  onOpen: (id: string) => void;
}) {
  const controller = useMemo(
    () => new LeaveBalancesController(leave.loadBalances, access, employeeId, query),
    [leave.loadBalances, access, employeeId, query],
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
  const filters = useLeaveBalanceFilters(query, onQuery);
  const rows = useMemo(
    () => (state.page ? leaveBalancesView(state.page, locale) : []),
    [state.page, locale],
  );
  const employee = useMemo(
    () =>
      state.page ? leaveBalanceEmployeeView(state.page.employee, state.page.year, locale) : null,
    [state.page, locale],
  );
  return (
    <LeaveBalancesPage
      state={state}
      rows={rows}
      employee={employee}
      companyName={companyName}
      locale={locale}
      filters={filters}
      requestsTo={`/leave/requests?${new URLSearchParams({ company: access.companyId, employee: employeeId })}`}
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
