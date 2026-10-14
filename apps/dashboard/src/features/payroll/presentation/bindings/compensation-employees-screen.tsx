import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { CompensationEmployeesController } from "../controllers/compensation-employees-controller";
import { useCompensationSearch } from "../controllers/use-compensation-search";
import {
  compensationEmployeeParameters,
  compensationEmployeeQuery,
  compensationReviewParameters,
} from "../models/compensation-route";
import { CompensationEmployeesPage } from "../pages/compensation-employees-page";
export function CompensationEmployeesScreen(props: PayrollScreenProps) {
  const [parameters, setParameters] = useSearchParams();
  const query = compensationEmployeeQuery(parameters, props.access.companyId);
  if (parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`/payroll/compensation?${compensationEmployeeParameters(props.access.companyId)}`}
        replace
      />
    );
  return (
    <CompensationEmployeesBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      query={query.query}
      after={query.after}
      onQuery={(query, after) =>
        setParameters(compensationEmployeeParameters(props.access.companyId, query, after))
      }
    />
  );
}
function CompensationEmployeesBinding({
  payroll,
  access,
  query,
  after,
  locale,
  companyName,
  timezone,
  onQuery,
}: PayrollScreenProps & {
  query: string;
  after: string | null;
  onQuery: (query: string, after: string | null) => void;
}) {
  const navigate = useNavigate();
  const controller = useMemo(
    () =>
      new CompensationEmployeesController(payroll.listCompensationEmployees, access, query, after),
    [payroll.listCompensationEmployees, access, query, after],
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
  const filter = useCompensationSearch(query, (query) => onQuery(query, null));
  return (
    <CompensationEmployeesPage
      state={state}
      filter={filter}
      locale={locale}
      companyName={companyName}
      firstPage={after === null}
      onRefresh={controller.refresh}
      onFirst={() => onQuery(query, null)}
      onNext={() => {
        if (state.page?.nextCursor) onQuery(query, state.page.nextCursor);
      }}
      onOpen={(id) => {
        if (state.stage !== "ready" || !state.page?.items.some((employee) => employee.id === id))
          return;
        navigate(
          `/payroll/compensation/${encodeURIComponent(id)}?${compensationReviewParameters(access.companyId, companyDate(timezone, new Date()).slice(0, 7))}`,
        );
      }}
    />
  );
}
