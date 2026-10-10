import { useEffect, useMemo, useSyncExternalStore } from "react";
import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { PersonProfileHistoryController } from "../controllers/person-profile-history-controller";
import { personProfileHistoryView, personProfileRevisionView } from "../models/person-profile-view";
import { PersonProfileHistoryPanel } from "../pages/person-profile-history-panel";

export function PersonProfileHistoryBinding({
  access,
  loadHistory,
  id,
  after,
  locale,
  onPage,
  onScopeFailure,
}: {
  access: CompanyAccess;
  loadHistory: PeopleUseCases["loadPersonProfileHistory"];
  id: string;
  after: string | null;
  locale: Locale;
  onPage: (after: string | null) => void;
  onScopeFailure: (failure: Failure) => void;
}) {
  const controller = useMemo(
    () => new PersonProfileHistoryController(loadHistory, access, id, after),
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
    () => (state.page ? personProfileHistoryView(state.page, locale) : []),
    [state.page, locale],
  );
  const revision = useMemo(
    () => (state.selected ? personProfileRevisionView(state.selected, locale) : null),
    [state.selected, locale],
  );
  return (
    <PersonProfileHistoryPanel
      state={state}
      rows={rows}
      revision={revision}
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
