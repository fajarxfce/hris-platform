import { useEffect, useMemo, useRef, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveBalanceQuery } from "../../domain/entities/leave-balance-query";
import { canAdjustLeaveBalance } from "../../domain/policies/leave-balance-adjustment-policy";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveLedgerController } from "../controllers/leave-ledger-controller";
import { balanceParameters, balanceQuery } from "../models/leave-balance-route";
import {
  leaveBalanceEmployeeView,
  leaveLedgerView,
  leaveMovementView,
} from "../models/leave-balance-view";
import { LeaveLedgerPage } from "../pages/leave-ledger-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  leave: LeaveUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
};
export function LeaveLedgerScreen(props: Props) {
  const { employeeId = "", typeId = "" } = useParams();
  const [parameters, setParameters] = useSearchParams();
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
        to={`${path}/${encodeURIComponent(typeId)}?${balanceParameters(props.access.companyId, query, directoryAfter)}`}
        replace
      />
    );
  return (
    <LeaveLedgerBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${typeId}`}
      {...props}
      employeeId={employeeId}
      typeId={typeId}
      query={query}
      backTo={`${path}?${balanceParameters(props.access.companyId, { year: query.year, after: directoryAfter })}`}
      adjustTo={`${path}/${encodeURIComponent(typeId)}/adjust?${balanceParameters(props.access.companyId, query, directoryAfter)}`}
      onPage={(after) =>
        setParameters(
          balanceParameters(props.access.companyId, { ...query, after }, directoryAfter),
        )
      }
    />
  );
}
function LeaveLedgerBinding({
  access,
  leave,
  companyName,
  timezone,
  locale,
  employeeId,
  typeId,
  query,
  backTo,
  adjustTo,
  onPage,
}: Props & {
  employeeId: string;
  typeId: string;
  query: LeaveBalanceQuery;
  backTo: string;
  adjustTo: string;
  onPage: (after: string | null) => void;
}) {
  const controller = useMemo(
    () => new LeaveLedgerController(leave.loadLedger, access, employeeId, typeId, query),
    [leave.loadLedger, access, employeeId, typeId, query],
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
  const selectionRef = useRef<HTMLElement>(null);
  useEffect(() => {
    if (state.selected) selectionRef.current?.focus();
  }, [state.selected]);
  const view = useMemo(
    () => (state.ledger ? leaveLedgerView(state.ledger, locale, timezone) : null),
    [state.ledger, locale, timezone],
  );
  const employee = useMemo(
    () =>
      state.ledger
        ? leaveBalanceEmployeeView(state.ledger.employee, state.ledger.balance.year, locale)
        : null,
    [state.ledger, locale],
  );
  const selection = useMemo(
    () => (state.selected ? leaveMovementView(state.selected, locale, timezone) : null),
    [state.selected, locale, timezone],
  );
  return (
    <LeaveLedgerPage
      state={state}
      view={view}
      employee={employee}
      selection={selection}
      selectionRef={selectionRef}
      relatedTo={
        state.selected?.requestId
          ? `/leave/requests/${encodeURIComponent(state.selected.requestId)}?${new URLSearchParams({ company: access.companyId, employee: employeeId })}`
          : null
      }
      companyName={companyName}
      timezone={timezone}
      locale={locale}
      backTo={backTo}
      adjustTo={state.ledger && canAdjustLeaveBalance(access, state.ledger) ? adjustTo : null}
      firstPage={query.after === null}
      onRefresh={controller.refresh}
      onFirst={() => onPage(null)}
      onOlder={() => {
        if (state.ledger?.nextCursor) onPage(state.ledger.nextCursor);
      }}
      onSelect={controller.select}
    />
  );
}
