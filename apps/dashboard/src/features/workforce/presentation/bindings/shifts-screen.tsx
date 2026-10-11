import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { ShiftSearch } from "../../domain/entities/shift-search";
import { canManageShifts } from "../../domain/policies/shift-policy";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { ShiftDirectoryController } from "../controllers/shift-directory-controller";
import { useShiftFilters } from "../controllers/use-shift-filters";
import { shiftParameters, shiftSearch, shiftsPath } from "../models/shift-route";
import { shiftDirectoryView } from "../models/shift-view";
import { ShiftsPage } from "../pages/shifts-page";

export function ShiftsScreen(props: WorkforceScreenProps) {
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const search = useMemo(
    () => shiftSearch(parameters, props.access.companyId),
    [parameters, props.access.companyId],
  );
  if (parameters.get("company") !== props.access.companyId)
    return (
      <Navigate to={`${shiftsPath}?${shiftParameters(props.access.companyId, search)}`} replace />
    );
  return (
    <ShiftsBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      search={search}
      onSearch={(next) => setParameters(shiftParameters(props.access.companyId, next))}
      onOpen={(id) =>
        navigate(
          `${shiftsPath}/${encodeURIComponent(id)}?${shiftParameters(props.access.companyId, search)}`,
        )
      }
    />
  );
}
function ShiftsBinding(
  props: WorkforceScreenProps & {
    search: ShiftSearch;
    onSearch: (search: ShiftSearch) => void;
    onOpen: (id: string) => void;
  },
) {
  const { workforce, access, search, locale } = props;
  const controller = useMemo(
    () => new ShiftDirectoryController(workforce.loadShifts, access, search),
    [workforce.loadShifts, access, search],
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
  const filters = useShiftFilters(search, locale, props.onSearch);
  const rows = useMemo(
    () => (state.page ? shiftDirectoryView(state.page, locale) : []),
    [state.page, locale],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <ShiftsPage
      state={state}
      rows={rows}
      filters={filters}
      companyName={props.companyName}
      locale={locale}
      firstPage={search.after === null}
      createTo={
        canManageShifts(access.permissions)
          ? `${shiftsPath}/new?${shiftParameters(access.companyId, search)}`
          : null
      }
      onRefresh={controller.refresh}
      onFirst={() => props.onSearch({ ...search, after: null })}
      onNext={() => {
        if (state.page?.nextCursor) props.onSearch({ ...search, after: state.page.nextCursor });
      }}
      onOpen={props.onOpen}
    />
  );
}
