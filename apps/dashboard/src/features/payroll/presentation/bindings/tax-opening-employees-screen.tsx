import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { TaxOpeningSearch } from "../../domain/entities/tax-opening-review";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { TaxOpeningEmployeesController } from "../controllers/tax-opening-employees-controller";
import { useTaxOpeningFilter } from "../controllers/use-tax-opening-filter";
import {
  taxOpeningParameters,
  taxOpeningReviewParameters,
  taxOpeningSearchFromRoute,
} from "../models/tax-opening-route";
import { TaxOpeningEmployeesPage } from "../pages/tax-opening-employees-page";
export function TaxOpeningEmployeesScreen(props: PayrollScreenProps) {
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(
    () => companyDate(props.timezone, new Date()).slice(0, 4),
    [props.timezone],
  );
  const search = taxOpeningSearchFromRoute(parameters, props.access.companyId, today);
  if (parameters.get("company") !== props.access.companyId || !parameters.has("year"))
    return (
      <Navigate
        to={`/payroll/tax-openings?${taxOpeningParameters(props.access.companyId, search)}`}
        replace
      />
    );
  return (
    <TaxOpeningEmployeesBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      search={search}
      onApply={(search) => setParameters(taxOpeningParameters(props.access.companyId, search))}
    />
  );
}
function TaxOpeningEmployeesBinding({
  payroll,
  access,
  search,
  locale,
  companyName,
  onApply,
}: PayrollScreenProps & {
  search: TaxOpeningSearch;
  onApply: (search: TaxOpeningSearch) => void;
}) {
  const navigate = useNavigate();
  const stableSearch = useMemo(
    () => ({
      year: search.year,
      query: search.query,
      status: search.status,
      after: search.after,
    }),
    [search.year, search.query, search.status, search.after],
  );
  const controller = useMemo(
    () => new TaxOpeningEmployeesController(payroll.listTaxOpeningEmployees, access, stableSearch),
    [payroll.listTaxOpeningEmployees, access, stableSearch],
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
  const filter = useTaxOpeningFilter(search, onApply);
  return (
    <TaxOpeningEmployeesPage
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
            `/payroll/tax-openings/${encodeURIComponent(id)}?${taxOpeningReviewParameters(access.companyId, search.year)}`,
          );
      }}
    />
  );
}
