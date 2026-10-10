import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeSearch } from "../../domain/entities/employee-search";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { EmployeeDirectoryController } from "../controllers/employee-directory-controller";
import { useEmployeeFilters } from "../controllers/use-employee-filters";
import { employeeSearchFromParameters, employeeSearchParameters } from "../models/employee-route";
import { employeeDirectoryView } from "../models/employee-view";
import { EmployeesPage } from "../pages/employees-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  people: PeopleUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
};

export function EmployeesScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = useMemo(
    () => employeeSearchFromParameters(parameters, props.access.companyId, today),
    [parameters, props.access.companyId, today],
  );
  if (parameters.get("company") !== props.access.companyId || !parameters.has("asOf"))
    return (
      <Navigate
        to={`/people/employees?${employeeSearchParameters(search, props.access.companyId)}`}
        replace
      />
    );
  return (
    <EmployeesBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      search={search}
      onSearch={(next) => setParameters(employeeSearchParameters(next, props.access.companyId))}
      onOpen={(id) =>
        navigate(
          `/people/employees/${encodeURIComponent(id)}?${employeeSearchParameters(search, props.access.companyId)}`,
        )
      }
    />
  );
}

function EmployeesBinding({
  access,
  people,
  companyName,
  locale,
  search,
  onSearch,
  onOpen,
}: Props & {
  search: EmployeeSearch;
  onSearch: (search: EmployeeSearch) => void;
  onOpen: (id: string) => void;
}) {
  const controller = useMemo(
    () => new EmployeeDirectoryController(people.loadEmployees, access, search),
    [people.loadEmployees, access, search],
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
  const filters = useEmployeeFilters(search, locale, onSearch);
  const rows = useMemo(
    () => (state.page ? employeeDirectoryView(state.page, locale) : []),
    [state.page, locale],
  );
  return (
    <EmployeesPage
      state={state}
      rows={rows}
      filters={filters}
      companyName={companyName}
      locale={locale}
      firstPage={search.after === null}
      onRefresh={controller.refresh}
      onFirst={() => onSearch({ ...search, after: null })}
      onNext={() => {
        if (state.page?.nextCursor) onSearch({ ...search, after: state.page.nextCursor });
      }}
      onOpen={onOpen}
    />
  );
}
