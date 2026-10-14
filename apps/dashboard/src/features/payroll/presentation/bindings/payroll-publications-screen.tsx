import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollPublicationSearch } from "../../domain/entities/payroll-publication-search";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollPublicationsController } from "../controllers/payroll-publications-controller";
import { usePayrollPublicationFilter } from "../controllers/use-payroll-publication-filter";
import {
  payrollFinalizationsPath,
  payrollPublicationParameters,
  payrollPublicationSearchFromRoute,
} from "../models/payroll-finalization-route";
import { payrollPublicationRows } from "../models/payroll-finalization-view";
import { PayrollPublicationsPage } from "../pages/payroll-publications-page";
export function PayrollPublicationsScreen(props: PayrollScreenProps) {
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = useMemo(
    () => payrollPublicationSearchFromRoute(parameters, props.access.companyId, today),
    [parameters, props.access.companyId, today],
  );
  if (
    parameters.get("company") !== props.access.companyId ||
    !parameters.has("from") ||
    !parameters.has("until")
  )
    return (
      <Navigate
        to={`/payroll/publications?${payrollPublicationParameters(props.access.companyId, search)}`}
        replace
      />
    );
  return (
    <PayrollPublicationsBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      search={search}
      onSearch={(search) =>
        setParameters(payrollPublicationParameters(props.access.companyId, search))
      }
    />
  );
}
function PayrollPublicationsBinding(
  props: PayrollScreenProps & {
    search: PayrollPublicationSearch;
    onSearch: (search: PayrollPublicationSearch) => void;
  },
) {
  const navigate = useNavigate(),
    { payroll, access, search, locale } = props;
  const controller = useMemo(
    () => new PayrollPublicationsController(payroll.listPublicationRuns, access, search),
    [payroll.listPublicationRuns, access, search],
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
  const filter = usePayrollPublicationFilter(search, props.onSearch),
    rows = useMemo(
      () => payrollPublicationRows(state.page?.items ?? [], locale),
      [state.page, locale],
    );
  return (
    <PayrollPublicationsPage
      state={state}
      filter={filter}
      rows={rows}
      locale={locale}
      companyName={props.companyName}
      firstPage={search.after === null}
      onOpen={(id) => {
        if (state.page?.items.some((row) => row.id === id))
          navigate(payrollFinalizationsPath(access.companyId, id));
      }}
      onRefresh={controller.refresh}
      onFirst={() => props.onSearch({ ...search, after: null })}
      onNext={() => {
        if (state.page?.nextCursor) props.onSearch({ ...search, after: state.page.nextCursor });
      }}
    />
  );
}
