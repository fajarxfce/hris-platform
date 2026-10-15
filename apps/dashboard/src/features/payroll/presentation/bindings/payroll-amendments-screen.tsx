import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollAmendmentSearch } from "../../domain/entities/payroll-amendment-search";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollAmendmentsController } from "../controllers/payroll-amendments-controller";
import { usePayrollAmendmentFilter } from "../controllers/use-payroll-amendment-filter";
import {
  payrollAmendmentParameters,
  payrollAmendmentPath,
  payrollAmendmentSearchFromRoute,
} from "../models/payroll-amendment-route";
import { payrollAmendmentRows } from "../models/payroll-amendment-view";
import { PayrollAmendmentsPage } from "../pages/payroll-amendments-page";
export function PayrollAmendmentsScreen(props: PayrollScreenProps) {
  const [parameters, setParameters] = useSearchParams(),
    year = useMemo(
      () => Number(companyDate(props.timezone, new Date()).slice(0, 4)),
      [props.timezone],
    ),
    search = useMemo(
      () => payrollAmendmentSearchFromRoute(parameters, props.access.companyId, year),
      [parameters, props.access.companyId, year],
    );
  if (parameters.get("company") !== props.access.companyId || !parameters.has("year"))
    return (
      <Navigate
        to={`/payroll/amendments?${payrollAmendmentParameters(props.access.companyId, search)}`}
        replace
      />
    );
  return (
    <PayrollAmendmentsBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      search={search}
      onSearch={(next) => setParameters(payrollAmendmentParameters(props.access.companyId, next))}
    />
  );
}
function PayrollAmendmentsBinding(
  props: PayrollScreenProps & {
    search: PayrollAmendmentSearch;
    onSearch: (search: PayrollAmendmentSearch) => void;
  },
) {
  const { payroll, access, search, locale, timezone } = props,
    navigate = useNavigate(),
    controller = useMemo(
      () => new PayrollAmendmentsController(payroll.listAmendments, access, search),
      [payroll.listAmendments, access, search],
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
  const filter = usePayrollAmendmentFilter(search, props.onSearch),
    rows = useMemo(
      () => payrollAmendmentRows(state.data?.items ?? [], locale, timezone),
      [state.data, locale, timezone],
    );
  return (
    <PayrollAmendmentsPage
      state={state}
      filter={filter}
      rows={rows}
      locale={locale}
      companyName={props.companyName}
      first={search.after === null}
      onOpen={(id) => {
        if (state.data?.items.some((row) => row.id === id))
          navigate(
            payrollAmendmentPath(
              access.companyId,
              id,
              payrollAmendmentParameters(access.companyId, search),
            ),
          );
      }}
      onRefresh={controller.refresh}
      onFirst={() => props.onSearch({ ...search, after: null })}
      onNext={() => {
        if (state.data?.nextCursor) props.onSearch({ ...search, after: state.data.nextCursor });
      }}
    />
  );
}
