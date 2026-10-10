import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { EmployeeImportController } from "../controllers/employee-import-controller";
import { employeeImportTransitionMessages } from "../i18n/employee-import-transition-messages";
import { employeeImportParameters } from "../models/employee-import-route";
import { employeeImportView } from "../models/employee-import-view";
import { EmployeeImportPage } from "../pages/employee-import-page";
import {
  EmployeeImportAttemptsBinding,
  EmployeeImportRowsBinding,
} from "./employee-import-results-binding";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  people: PeopleUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
};
export function EmployeeImportScreen(props: Props) {
  const { importId = "" } = useParams();
  const [parameters, setParameters] = useSearchParams();
  const query = employeeImportParameters(
    props.access.companyId,
    parameters.get("company") === props.access.companyId ? parameters.get("after") : null,
  ).toString();
  const backTo = `/people/imports?${query}`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={backTo} replace />;
  if (!parameters.has("company")) {
    const canonical = new URLSearchParams(parameters);
    canonical.set("company", props.access.companyId);
    return <Navigate to={`/people/imports/${encodeURIComponent(importId)}?${canonical}`} replace />;
  }
  const tab =
    parameters.get("tab") === "rows"
      ? "rows"
      : parameters.get("tab") === "attempts"
        ? "attempts"
        : "overview";
  return (
    <EmployeeImportBinding
      key={`${props.accountId}:${props.access.companyId}:${importId}`}
      {...props}
      id={importId}
      backTo={backTo}
      query={query}
      tab={tab}
      rowsAfter={parameters.get("rowsAfter")}
      attemptsAfter={parameters.get("attemptsAfter")}
      onParameter={(key, value) => {
        const next = new URLSearchParams(parameters);
        if (value === null) next.delete(key);
        else next.set(key, value);
        setParameters(next);
      }}
    />
  );
}
function EmployeeImportBinding({
  access,
  people,
  companyName,
  timezone,
  locale,
  id,
  backTo,
  query,
  tab,
  rowsAfter,
  attemptsAfter,
  onParameter,
}: Props & {
  id: string;
  backTo: string;
  query: string;
  tab: "overview" | "rows" | "attempts";
  rowsAfter: string | null;
  attemptsAfter: string | null;
  onParameter: (key: string, value: string | null) => void;
}) {
  const controller = useMemo(
    () => new EmployeeImportController(people.loadEmployeeImport, access, id),
    [people.loadEmployeeImport, access, id],
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
  const view = useMemo(
    () => (state.summary ? employeeImportView(state.summary, locale, timezone) : null),
    [state.summary, locale, timezone],
  );
  return (
    <EmployeeImportPage
      state={state}
      view={view}
      companyName={companyName}
      locale={locale}
      backTo={backTo}
      actions={
        state.summary?.availableActions.map((action) => ({
          action,
          label: employeeImportTransitionMessages(locale)[action],
          to: `/people/imports/${encodeURIComponent(id)}/${action}?${query}`,
        })) ?? []
      }
      tab={tab}
      onTab={(selected) => onParameter("tab", selected)}
      onRefresh={controller.refresh}
      results={
        tab === "rows" ? (
          <EmployeeImportRowsBinding
            access={access}
            load={people.loadEmployeeImportRows}
            id={id}
            after={rowsAfter}
            locale={locale}
            onPage={(after) => onParameter("rowsAfter", after)}
            onScopeFailure={controller.reportScopeFailure}
          />
        ) : (
          <EmployeeImportAttemptsBinding
            access={access}
            load={people.loadEmployeeImportAttempts}
            id={id}
            after={attemptsAfter}
            locale={locale}
            timezone={timezone}
            onPage={(after) => onParameter("attemptsAfter", after)}
            onScopeFailure={controller.reportScopeFailure}
          />
        )
      }
    />
  );
}
