import { useMemo } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { OvertimeSearchInput } from "../../domain/entities/overtime-search";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { useOvertimeFilters } from "../controllers/use-overtime-filters";
import { useOvertimeRequests } from "../controllers/use-overtime-requests";
import {
  overtimeParameters,
  overtimePath,
  overtimeRequestPath,
  overtimeSearchFromRoute,
} from "../models/overtime-route";
import { overtimeRows } from "../models/overtime-view";
import { OvertimeRequestsPage } from "../pages/overtime-requests-page";

export function OvertimeRequestsScreen(props: WorkforceScreenProps) {
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = useMemo(
    () => overtimeSearchFromRoute(parameters, props.access.companyId, today),
    [parameters, props.access.companyId, today],
  );
  if (parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`${overtimePath}?${overtimeParameters(props.access.companyId, search)}`}
        replace
      />
    );
  return (
    <OvertimeRequestsBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      search={search}
      onSearch={(next) => setParameters(overtimeParameters(props.access.companyId, next))}
    />
  );
}

function OvertimeRequestsBinding(
  props: WorkforceScreenProps & {
    search: OvertimeSearchInput;
    onSearch: (search: OvertimeSearchInput) => void;
  },
) {
  const navigate = useNavigate();
  const state = useOvertimeRequests(
    props.accountId,
    props.access,
    props.search,
    props.workforce.loadOvertimeRequests,
  );
  const filters = useOvertimeFilters(props.search, props.locale, props.onSearch);
  const rows = useMemo(
    () => (state.page ? overtimeRows(state.page, props.locale) : []),
    [state.page, props.locale],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <OvertimeRequestsPage
      state={state}
      filters={filters}
      rows={rows}
      locale={props.locale}
      companyName={props.companyName}
      firstPage={props.search.after === null}
      canCreate={props.access.permissions.includes("overtime.manage")}
      onCreate={() =>
        navigate(`${overtimePath}/new?${overtimeParameters(props.access.companyId, props.search)}`)
      }
      onFirst={() => props.onSearch({ ...props.search, after: null })}
      onNext={() => {
        if (state.page?.nextCursor)
          props.onSearch({ ...props.search, after: state.page.nextCursor });
      }}
      onOpen={(id) => {
        if (state.page?.items.some((item) => item.id === id))
          navigate(
            `${overtimeRequestPath(id)}?${overtimeParameters(props.access.companyId, props.search)}`,
          );
      }}
    />
  );
}
