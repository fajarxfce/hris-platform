import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { HolidayRange } from "../../domain/entities/holiday-range";
import { canManageHolidays } from "../../domain/policies/holiday-policy";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { HolidayDirectoryController } from "../controllers/holiday-directory-controller";
import { useHolidayFilters } from "../controllers/use-holiday-filters";
import { holidayParameters, holidayRange, holidaysPath } from "../models/holiday-route";
import { holidayDirectoryView } from "../models/holiday-view";
import { HolidaysPage } from "../pages/holidays-page";

export function HolidaysScreen(props: WorkforceScreenProps) {
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const range = useMemo(
    () => holidayRange(parameters, props.access.companyId, today),
    [parameters, props.access.companyId, today],
  );
  if (parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`${holidaysPath}?${holidayParameters(props.access.companyId, range)}`}
        replace
      />
    );
  return (
    <HolidaysBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      range={range}
      onRange={(next) => setParameters(holidayParameters(props.access.companyId, next))}
      onOpen={(id) =>
        navigate(
          `${holidaysPath}/${encodeURIComponent(id)}?${holidayParameters(props.access.companyId, range)}`,
        )
      }
    />
  );
}
function HolidaysBinding(
  props: WorkforceScreenProps & {
    range: HolidayRange;
    onRange: (range: HolidayRange) => void;
    onOpen: (id: string) => void;
  },
) {
  const { workforce, access, range, locale } = props;
  const controller = useMemo(
    () => new HolidayDirectoryController(workforce.loadHolidays, access, range),
    [workforce.loadHolidays, access, range],
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
  const filters = useHolidayFilters(range, locale, props.onRange);
  const rows = useMemo(
    () => (state.holidays ? holidayDirectoryView(state.holidays, locale) : []),
    [state.holidays, locale],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <HolidaysPage
      state={state}
      rows={rows}
      filters={filters}
      companyName={props.companyName}
      locale={locale}
      createTo={
        canManageHolidays(access.permissions)
          ? `${holidaysPath}/new?${holidayParameters(access.companyId, range)}`
          : null
      }
      onRefresh={controller.refresh}
      onOpen={props.onOpen}
    />
  );
}
