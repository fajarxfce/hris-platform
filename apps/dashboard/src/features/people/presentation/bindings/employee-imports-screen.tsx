import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canImportEmployees } from "../../domain/policies/employee-import-policy";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { EmployeeImportsController } from "../controllers/employee-imports-controller";
import { employeeImportParameters } from "../models/employee-import-route";
import { employeeImportsView } from "../models/employee-import-view";
import { EmployeeImportsPage } from "../pages/employee-imports-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  people: PeopleUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
};
export function EmployeeImportsScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const after =
    parameters.get("company") === props.access.companyId ? parameters.get("after") : null;
  const canonical = employeeImportParameters(props.access.companyId, after);
  if (parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/people/imports?${canonical}`} replace />;
  return (
    <EmployeeImportsBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      after={after}
      onPage={(cursor) => setParameters(employeeImportParameters(props.access.companyId, cursor))}
      onOpen={(id) => navigate(`/people/imports/${encodeURIComponent(id)}?${canonical}`)}
    />
  );
}
function EmployeeImportsBinding({
  access,
  people,
  companyName,
  timezone,
  locale,
  after,
  onPage,
  onOpen,
}: Props & {
  after: string | null;
  onPage: (after: string | null) => void;
  onOpen: (id: string) => void;
}) {
  const controller = useMemo(
    () => new EmployeeImportsController(people.loadEmployeeImports, access, after),
    [people.loadEmployeeImports, access, after],
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
  const rows = useMemo(
    () => (state.page ? employeeImportsView(state.page, locale, timezone) : []),
    [state.page, locale, timezone],
  );
  return (
    <EmployeeImportsPage
      state={state}
      rows={rows}
      companyName={companyName}
      locale={locale}
      firstPage={after === null}
      createTo={
        canImportEmployees(access.permissions)
          ? `/people/imports/new?${employeeImportParameters(access.companyId, after)}`
          : null
      }
      onRefresh={controller.refresh}
      onFirst={() => onPage(null)}
      onNext={() => {
        if (state.page?.nextCursor) onPage(state.page.nextCursor);
      }}
      onOpen={onOpen}
    />
  );
}
