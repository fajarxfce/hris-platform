import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveBalanceAdjustmentController } from "../controllers/leave-balance-adjustment-controller";
import { useLeaveBalanceAdjustmentForm } from "../controllers/use-leave-balance-adjustment-form";
import { balanceParameters, balanceQuery } from "../models/leave-balance-route";
import { leaveBalanceEmployeeView, leaveLedgerView } from "../models/leave-balance-view";
import { LeaveBalanceAdjustmentPage } from "../pages/leave-balance-adjustment-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  leave: LeaveUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
  nextIdentifier: () => string;
};
export function LeaveBalanceAdjustmentScreen(props: Props) {
  const { employeeId = "", typeId = "" } = useParams();
  const [parameters] = useSearchParams();
  const defaultYear = useMemo(
    () => companyDate(props.timezone, new Date()).slice(0, 4),
    [props.timezone],
  );
  const query = balanceQuery(parameters, defaultYear);
  const directoryAfter = parameters.get("directoryAfter");
  const fromTypes = parameters.get("source") === "types";
  const path = `/leave/employees/${encodeURIComponent(employeeId)}/balances`;
  const context = balanceParameters(props.access.companyId, query, directoryAfter);
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to="/" replace />;
  if (!parameters.has("company") || !parameters.has("year"))
    return (
      <Navigate
        to={`${path}/${encodeURIComponent(typeId)}/adjust?${context}${fromTypes ? "&source=types" : ""}`}
        replace
      />
    );
  return (
    <LeaveBalanceAdjustmentBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${typeId}:${query.year}`}
      {...props}
      employeeId={employeeId}
      typeId={typeId}
      year={query.year}
      fromTypes={fromTypes}
      backTo={`${path}/${fromTypes ? "adjust" : encodeURIComponent(typeId)}?${context}`}
      ledgerTo={`${path}/${encodeURIComponent(typeId)}?${balanceParameters(props.access.companyId, { year: query.year, after: null }, directoryAfter)}`}
    />
  );
}
function LeaveBalanceAdjustmentBinding({
  access,
  leave,
  companyName,
  timezone,
  locale,
  employeeId,
  typeId,
  year,
  fromTypes,
  nextIdentifier,
  backTo,
  ledgerTo,
}: Props & {
  employeeId: string;
  typeId: string;
  year: string;
  fromTypes: boolean;
  backTo: string;
  ledgerTo: string;
}) {
  const controller = useMemo(
    () =>
      new LeaveBalanceAdjustmentController(leave, access, employeeId, typeId, year, nextIdentifier),
    [leave, access, employeeId, typeId, year, nextIdentifier],
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
  const form = useLeaveBalanceAdjustmentForm(controller, state);
  const view = useMemo(
    () => (state.review ? leaveLedgerView(state.review, locale, timezone) : null),
    [state.review, locale, timezone],
  );
  const employee = useMemo(
    () =>
      state.review
        ? leaveBalanceEmployeeView(state.review.employee, state.review.balance.year, locale)
        : null,
    [state.review, locale],
  );
  return (
    <LeaveBalanceAdjustmentPage
      state={state}
      form={form}
      view={view}
      employee={employee}
      companyName={companyName}
      locale={locale}
      backTo={backTo}
      fromTypes={fromTypes}
      ledgerTo={ledgerTo}
      onRetry={controller.retry}
    />
  );
}
