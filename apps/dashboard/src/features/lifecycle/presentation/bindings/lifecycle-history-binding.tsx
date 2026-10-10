import { useEffect, useMemo, useSyncExternalStore } from "react";
import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { LifecycleHistoryController } from "../controllers/lifecycle-history-controller";
import { lifecycleEventView, lifecycleHistoryView } from "../models/lifecycle-history-view";
import { LifecycleHistoryPage } from "../pages/lifecycle-history-page";

export function LifecycleHistoryBinding({
  load,
  access,
  id,
  after,
  locale,
  timezone,
  onPage,
  onScopeFailure,
}: {
  load: LifecycleUseCases["loadHistory"];
  access: CompanyAccess;
  id: string;
  after: string | null;
  locale: Locale;
  timezone: string;
  onPage: (after: string | null) => void;
  onScopeFailure: (failure: Failure) => void;
}) {
  const controller = useMemo(
    () => new LifecycleHistoryController(load, access, id, after),
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
    () => (state.page ? lifecycleHistoryView(state.page, locale, timezone) : []),
    [state.page, locale, timezone],
  );
  const selected = useMemo(
    () => (state.selected ? lifecycleEventView(state.selected, locale, timezone) : null),
    [state.selected, locale, timezone],
  );
  return (
    <LifecycleHistoryPage
      state={state}
      rows={rows}
      selected={selected}
      firstPage={after === null}
      locale={locale}
      timezone={timezone}
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
