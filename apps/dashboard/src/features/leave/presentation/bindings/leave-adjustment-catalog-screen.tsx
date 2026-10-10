import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveBalanceQuery } from "../../domain/entities/leave-balance-query";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveAdjustmentCatalogController } from "../controllers/leave-adjustment-catalog-controller";
import { balanceParameters, balanceQuery } from "../models/leave-balance-route";
import { leaveBalanceEmployeeView } from "../models/leave-balance-view";
import { leavePoliciesView } from "../models/leave-policy-view";
import { LeaveAdjustmentCatalogPage } from "../pages/leave-adjustment-catalog-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  leave: LeaveUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
};
export function LeaveAdjustmentCatalogScreen(props: Props) {
  const { employeeId = "" } = useParams();
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const defaultYear = useMemo(
    () => companyDate(props.timezone, new Date()).slice(0, 4),
    [props.timezone],
  );
  const query = useMemo(() => balanceQuery(parameters, defaultYear), [parameters, defaultYear]);
  const directoryAfter = parameters.get("directoryAfter");
  const path = `/leave/employees/${encodeURIComponent(employeeId)}/balances`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to="/" replace />;
  if (!parameters.has("company") || !parameters.has("year"))
    return (
      <Navigate
        to={`${path}/adjust?${balanceParameters(props.access.companyId, query, directoryAfter)}`}
        replace
      />
    );
  return (
    <LeaveAdjustmentCatalogBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}`}
      {...props}
      employeeId={employeeId}
      query={query}
      backTo={`${path}?${balanceParameters(props.access.companyId, { year: query.year, after: directoryAfter })}`}
      onPage={(after) =>
        setParameters(
          balanceParameters(props.access.companyId, { ...query, after }, directoryAfter),
        )
      }
      onOpen={(id) =>
        navigate(
          `${path}/${encodeURIComponent(id)}/adjust?${balanceParameters(props.access.companyId, query, directoryAfter)}&source=types`,
        )
      }
    />
  );
}
function LeaveAdjustmentCatalogBinding({
  access,
  leave,
  companyName,
  locale,
  employeeId,
  query,
  backTo,
  onPage,
  onOpen,
}: Props & {
  employeeId: string;
  query: LeaveBalanceQuery;
  backTo: string;
  onPage: (after: string | null) => void;
  onOpen: (id: string) => void;
}) {
  const controller = useMemo(
    () =>
      new LeaveAdjustmentCatalogController(leave.loadAdjustmentCatalog, access, employeeId, query),
    [leave.loadAdjustmentCatalog, access, employeeId, query],
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
  const rows = useMemo(
    () => (state.catalog ? leavePoliciesView(state.catalog.policies, locale) : []),
    [state.catalog, locale],
  );
  const employee = useMemo(
    () =>
      state.catalog
        ? leaveBalanceEmployeeView(state.catalog.employee, state.catalog.year, locale)
        : null,
    [state.catalog, locale],
  );
  return (
    <LeaveAdjustmentCatalogPage
      state={state}
      rows={rows}
      employee={employee}
      companyName={companyName}
      locale={locale}
      backTo={backTo}
      firstPage={query.after === null}
      onRefresh={controller.refresh}
      onFirst={() => onPage(null)}
      onNext={() => {
        if (state.catalog?.policies.nextCursor) onPage(state.catalog.policies.nextCursor);
      }}
      onOpen={onOpen}
    />
  );
}
