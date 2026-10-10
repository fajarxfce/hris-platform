import { useEffect, useMemo, useSyncExternalStore } from "react";
import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReviewEmploymentCancellation } from "../../domain/policies/employment-cancellation-policy";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { EmploymentHistoryController } from "../controllers/employment-history-controller";
import { employmentHistoryView, employmentRevisionView } from "../models/employment-history-view";
import { EmploymentHistoryPanel } from "../pages/employment-history-panel";

export function EmploymentHistoryBinding({
  access,
  loadHistory,
  id,
  after,
  asOf,
  today,
  locale,
  onPage,
  onScopeFailure,
}: {
  access: CompanyAccess;
  loadHistory: PeopleUseCases["loadEmploymentHistory"];
  id: string;
  after: string | null;
  asOf: string;
  today: string;
  locale: Locale;
  onPage: (after: string | null) => void;
  onScopeFailure: (failure: Failure) => void;
}) {
  const controller = useMemo(
    () => new EmploymentHistoryController(loadHistory, access, id, after),
    [loadHistory, access, id, after],
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
    () => (state.page ? employmentHistoryView(state.page, locale) : []),
    [state.page, locale],
  );
  const revision = useMemo(
    () => (state.selected ? employmentRevisionView(state.selected, locale) : null),
    [state.selected, locale],
  );
  return (
    <EmploymentHistoryPanel
      state={state}
      rows={rows}
      revision={revision}
      cancelTo={
        state.selected && canReviewEmploymentCancellation(access.permissions, state.selected, today)
          ? `/people/employees/${encodeURIComponent(id)}/revisions/${state.selected.revision}/cancel?${new URLSearchParams({ company: access.companyId, asOf })}`
          : null
      }
      locale={locale}
      firstPage={after === null}
      onRefresh={controller.refresh}
      onFirst={() => onPage(null)}
      onNext={() => {
        if (state.page?.nextCursor) onPage(state.page.nextCursor);
      }}
      onOpen={controller.openRevision}
      onClose={controller.closeRevision}
    />
  );
}
