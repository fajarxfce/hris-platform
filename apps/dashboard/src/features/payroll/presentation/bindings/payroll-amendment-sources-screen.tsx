import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollAmendmentSearch } from "../../domain/entities/payroll-amendment-search";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollAmendmentSourcesController } from "../controllers/payroll-amendment-sources-controller";
import { usePayrollAmendmentFilter } from "../controllers/use-payroll-amendment-filter";
import { payrollAmendmentCandidateRows } from "../models/payroll-amendment-editor-view";
import {
  payrollAmendmentParameters,
  payrollAmendmentSearchFromRoute,
} from "../models/payroll-amendment-route";
import { PayrollAmendmentSourcesPage } from "../pages/payroll-amendment-sources-page";
export function PayrollAmendmentSourcesScreen(props: PayrollScreenProps) {
  const [parameters, setParameters] = useSearchParams(),
    year = useMemo(
      () => Number(companyDate(props.timezone, new Date()).slice(0, 4)),
      [props.timezone],
    );
  const search = useMemo(
    () => ({
      ...payrollAmendmentSearchFromRoute(parameters, props.access.companyId, year),
      status: null,
    }),
    [parameters, props.access.companyId, year],
  );
  if (parameters.get("company") !== props.access.companyId || !parameters.has("year"))
    return (
      <Navigate
        to={`/payroll/amendments/new?${payrollAmendmentParameters(props.access.companyId, search)}`}
        replace
      />
    );
  return (
    <PayrollAmendmentSourcesBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      search={search}
      onSearch={(next) =>
        setParameters(payrollAmendmentParameters(props.access.companyId, { ...next, status: null }))
      }
    />
  );
}
function PayrollAmendmentSourcesBinding(
  props: PayrollScreenProps & {
    search: PayrollAmendmentSearch;
    onSearch: (search: PayrollAmendmentSearch) => void;
  },
) {
  const { payroll, access, search, locale, timezone } = props,
    navigate = useNavigate(),
    controller = useMemo(
      () => new PayrollAmendmentSourcesController(payroll.listAmendmentSources, access, search),
      [payroll.listAmendmentSources, access, search],
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
      () => payrollAmendmentCandidateRows(state.data?.items ?? [], locale, timezone),
      [state.data, locale, timezone],
    );
  return (
    <PayrollAmendmentSourcesPage
      state={state}
      filter={filter}
      rows={rows}
      locale={locale}
      companyName={props.companyName}
      backTo={`/payroll/amendments?${payrollAmendmentParameters(access.companyId, { ...search, after: null })}`}
      first={search.after === null}
      onRefresh={controller.refresh}
      onFirst={() => props.onSearch({ ...search, after: null })}
      onNext={() => {
        if (state.data?.nextCursor) props.onSearch({ ...search, after: state.data.nextCursor });
      }}
      onOpen={(id) => {
        if (state.data?.items.some((row) => row.assessmentId === id))
          navigate(
            `/payroll/amendments/new/${id}?${payrollAmendmentParameters(access.companyId, search)}`,
          );
      }}
    />
  );
}
