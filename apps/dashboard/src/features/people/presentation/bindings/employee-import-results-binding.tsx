import { useEffect, useMemo, useSyncExternalStore } from "react";
import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { EmployeeImportAttemptsController } from "../controllers/employee-import-attempts-controller";
import { EmployeeImportRowsController } from "../controllers/employee-import-rows-controller";
import {
  employeeImportAttemptsView,
  employeeImportRowsView,
  employeeImportRowView,
} from "../models/employee-import-view";
import { EmployeeImportAttemptsPage } from "../pages/employee-import-attempts-page";
import { EmployeeImportRowsPage } from "../pages/employee-import-rows-page";

type ResultsProps = {
  access: CompanyAccess;
  id: string;
  after: string | null;
  locale: Locale;
  onPage: (after: string | null) => void;
  onScopeFailure: (failure: Failure) => void;
};
export function EmployeeImportRowsBinding({
  load,
  access,
  id,
  after,
  locale,
  onPage,
  onScopeFailure,
}: ResultsProps & { load: PeopleUseCases["loadEmployeeImportRows"] }) {
  const controller = useMemo(
    () => new EmployeeImportRowsController(load, access, id, after),
    [load, access, id, after],
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
  useEffect(() => {
    if (state.failure) onScopeFailure(state.failure);
  }, [state.failure, onScopeFailure]);
  const rows = useMemo(
    () => (state.page ? employeeImportRowsView(state.page, locale) : []),
    [state.page, locale],
  );
  const selected = useMemo(
    () => (state.selected ? employeeImportRowView(state.selected, locale) : null),
    [state.selected, locale],
  );
  return (
    <EmployeeImportRowsPage
      state={state}
      rows={rows}
      selected={selected}
      locale={locale}
      firstPage={after === null}
      onRefresh={controller.refresh}
      onFirst={() => onPage(null)}
      onNext={() => {
        if (state.page?.nextCursor) onPage(state.page.nextCursor);
      }}
      onOpen={controller.open}
      onClose={controller.close}
    />
  );
}
export function EmployeeImportAttemptsBinding({
  load,
  access,
  id,
  after,
  locale,
  timezone,
  onPage,
  onScopeFailure,
}: ResultsProps & { load: PeopleUseCases["loadEmployeeImportAttempts"]; timezone: string }) {
  const controller = useMemo(
    () => new EmployeeImportAttemptsController(load, access, id, after),
    [load, access, id, after],
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
  useEffect(() => {
    if (state.failure) onScopeFailure(state.failure);
  }, [state.failure, onScopeFailure]);
  const rows = useMemo(
    () => (state.page ? employeeImportAttemptsView(state.page, locale, timezone) : []),
    [state.page, locale, timezone],
  );
  return (
    <EmployeeImportAttemptsPage
      state={state}
      rows={rows}
      locale={locale}
      firstPage={after === null}
      onRefresh={controller.refresh}
      onFirst={() => onPage(null)}
      onNext={() => {
        if (state.page?.nextCursor) onPage(state.page.nextCursor);
      }}
    />
  );
}
