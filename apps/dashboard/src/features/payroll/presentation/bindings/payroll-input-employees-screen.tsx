import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollInputSearch } from "../../domain/entities/payroll-input-review";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollInputEmployeesController } from "../controllers/payroll-input-employees-controller";
import { usePayrollInputFilter } from "../controllers/use-payroll-input-filter";
import {
  payrollInputParameters,
  payrollInputReviewParameters,
  payrollInputSearchFromRoute,
} from "../models/payroll-input-route";
import { PayrollInputEmployeesPage } from "../pages/payroll-input-employees-page";
export function PayrollInputEmployeesScreen(props: PayrollScreenProps) {
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(
    () => companyDate(props.timezone, new Date()).slice(0, 7),
    [props.timezone],
  );
  const search = payrollInputSearchFromRoute(parameters, props.access.companyId, today);
  if (parameters.get("company") !== props.access.companyId || !parameters.has("month"))
    return (
      <Navigate
        to={`/payroll/inputs?${payrollInputParameters(props.access.companyId, search)}`}
        replace
      />
    );
  return (
    <PayrollInputEmployeesBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      search={search}
      onApply={(search) => setParameters(payrollInputParameters(props.access.companyId, search))}
    />
  );
}
function PayrollInputEmployeesBinding({
  payroll,
  access,
  search,
  locale,
  companyName,
  onApply,
}: PayrollScreenProps & {
  search: PayrollInputSearch;
  onApply: (search: PayrollInputSearch) => void;
}) {
  const navigate = useNavigate();
  const stableSearch = useMemo(
    () => ({
      month: search.month,
      query: search.query,
      status: search.status,
      after: search.after,
    }),
    [search.month, search.query, search.status, search.after],
  );
  const controller = useMemo(
    () =>
      new PayrollInputEmployeesController(payroll.listPayrollInputEmployees, access, stableSearch),
    [payroll.listPayrollInputEmployees, access, stableSearch],
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
  const filter = usePayrollInputFilter(search, onApply);
  return (
    <PayrollInputEmployeesPage
      state={state}
      filter={filter}
      locale={locale}
      companyName={companyName}
      first={search.after === null}
      onRefresh={controller.refresh}
      onFirst={() => onApply({ ...search, after: null })}
      onNext={() => {
        if (state.page?.nextCursor) onApply({ ...search, after: state.page.nextCursor });
      }}
      onOpen={(id) => {
        if (state.stage === "ready" && state.page?.items.some((row) => row.id === id))
          navigate(
            `/payroll/inputs/${encodeURIComponent(id)}?${payrollInputReviewParameters(access.companyId, search.month)}`,
          );
      }}
    />
  );
}
