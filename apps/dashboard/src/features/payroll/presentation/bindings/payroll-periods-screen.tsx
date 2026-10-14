import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollPeriodSearch } from "../../domain/entities/payroll-period-search";
import { canPreparePayrollPeriod } from "../../domain/policies/payroll-period-policy";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollPeriodsController } from "../controllers/payroll-periods-controller";
import { usePayrollPeriodFilter } from "../controllers/use-payroll-period-filter";
import {
  payrollPeriodParameters,
  payrollPeriodSearchFromRoute,
} from "../models/payroll-period-route";
import { payrollPeriodRows } from "../models/payroll-period-view";
import { PayrollPeriodsPage } from "../pages/payroll-periods-page";
export function PayrollPeriodsScreen(props: PayrollScreenProps) {
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = useMemo(
    () => payrollPeriodSearchFromRoute(parameters, props.access.companyId, today),
    [parameters, props.access.companyId, today],
  );
  if (
    parameters.get("company") !== props.access.companyId ||
    !parameters.has("from") ||
    !parameters.has("until")
  )
    return (
      <Navigate
        to={`/payroll/periods?${payrollPeriodParameters(props.access.companyId, search)}`}
        replace
      />
    );
  return (
    <PayrollPeriodsBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      search={search}
      onSearch={(search) => setParameters(payrollPeriodParameters(props.access.companyId, search))}
    />
  );
}
function PayrollPeriodsBinding(
  props: PayrollScreenProps & {
    search: PayrollPeriodSearch;
    onSearch: (search: PayrollPeriodSearch) => void;
  },
) {
  const navigate = useNavigate(),
    { payroll, access, search } = props;
  const controller = useMemo(
    () => new PayrollPeriodsController(payroll.listPeriods, access, search),
    [payroll.listPeriods, access, search],
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
  const filter = usePayrollPeriodFilter(search, props.onSearch);
  const rows = useMemo(
    () => payrollPeriodRows(state.page?.items ?? [], props.locale),
    [state.page, props.locale],
  );
  return (
    <PayrollPeriodsPage
      state={state}
      filter={filter}
      rows={rows}
      locale={props.locale}
      companyName={props.companyName}
      canCreate={canPreparePayrollPeriod(access.permissions)}
      firstPage={search.after === null}
      onCreate={() =>
        navigate(
          `/payroll/periods/new?company=${access.companyId}&month=${companyDate(props.timezone, new Date()).slice(0, 7)}`,
        )
      }
      onOpen={(id) => {
        if (state.page?.items.some((row) => row.id === id))
          navigate(`/payroll/periods/${encodeURIComponent(id)}?company=${access.companyId}`);
      }}
      onRefresh={controller.refresh}
      onFirst={() => props.onSearch({ ...search, after: null })}
      onNext={() => {
        if (state.page?.nextCursor) props.onSearch({ ...search, after: state.page.nextCursor });
      }}
    />
  );
}
