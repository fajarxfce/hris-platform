import { useEffect, useMemo, useSyncExternalStore } from "react";
import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess, CompanyMembership } from "../../../identity/domain/entities/session";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { EmploymentTransfersController } from "../controllers/employment-transfers-controller";
import { employmentTransfersView } from "../models/employment-transfers-view";
import { EmploymentTransfersPanel } from "../pages/employment-transfers-panel";

export function EmploymentTransfersBinding({
  access,
  load,
  id,
  companies,
  locale,
  onScopeFailure,
}: {
  access: CompanyAccess;
  load: PeopleUseCases["loadEmploymentTransfers"];
  id: string;
  companies: readonly CompanyMembership[];
  locale: Locale;
  onScopeFailure: (failure: Failure) => void;
}) {
  const controller = useMemo(
    () => new EmploymentTransfersController(load, access, id),
    [load, access, id],
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
  useEffect(() => {
    if (state.failure) onScopeFailure(state.failure);
  }, [state.failure, onScopeFailure]);
  const rows = useMemo(
    () => employmentTransfersView(state.items ?? [], access.companyId, companies, locale),
    [state.items, access.companyId, companies, locale],
  );
  return (
    <EmploymentTransfersPanel
      state={state}
      rows={rows}
      locale={locale}
      onRefresh={controller.refresh}
    />
  );
}
