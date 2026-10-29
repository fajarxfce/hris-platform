import { useContext, useEffect, useMemo, useSyncExternalStore } from "react";
import { useSearchParams } from "react-router-dom";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PlatformAccess } from "../../../identity/domain/entities/platform-access";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import { PlatformAuditController } from "../controllers/platform-audit-controller";
import { useAuditFilters } from "../controllers/use-audit-filters";
import { administrationMessages } from "../i18n/administration-messages";
import { auditEventView, auditPageView } from "../models/audit-view";
import {
  platformAuditSearchFromParameters,
  platformAuditSearchParameters,
} from "../models/platform-audit-route";
import { AuditPage } from "../pages/audit-page";

export function PlatformAuditScreen(props: {
  access: PlatformAccess;
  search: AdministrationUseCases["searchPlatformAudit"];
  locale: Locale;
}) {
  const visible = useContext(WorkspaceVisibilityContext);
  const [parameters, setParameters] = useSearchParams();
  const query = useMemo(() => platformAuditSearchFromParameters(parameters), [parameters]);
  const accountId = props.access.accountId;
  const permitted = props.access.permissions.includes("audit.read");
  const access = useMemo(
    () => Object.freeze({ accountId, permissions: Object.freeze(permitted ? ["audit.read"] : []) }),
    [accountId, permitted],
  );
  const controller = useMemo(
    () => new PlatformAuditController(props.search, access, query),
    [props.search, access, query],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    if (visible) controller.activate();
    else controller.deactivate();
    return controller.deactivate;
  }, [visible, controller]);
  useWorkspaceRevalidation(state.failure);
  const onQueryChanged = (next: typeof query) => setParameters(platformAuditSearchParameters(next));
  const filters = useAuditFilters(query, props.locale, onQueryChanged);
  const view = useMemo(
    () => (state.page ? auditPageView(state.page, props.locale) : null),
    [state.page, props.locale],
  );
  const event = useMemo(
    () => (state.selected ? auditEventView(state.selected, props.locale) : null),
    [state.selected, props.locale],
  );
  return (
    <AuditPage
      platform
      state={state}
      view={view}
      event={event}
      filters={filters}
      companyName={administrationMessages(props.locale).platform}
      locale={props.locale}
      onRefresh={controller.refresh}
      onOpen={controller.openEvent}
      onClose={controller.closeEvent}
      hasCursor={query.cursor !== null}
      onReset={filters.reset}
      onFirst={() => onQueryChanged({ ...query, cursor: null })}
      onOlder={() => {
        if (state.page?.nextCursor)
          onQueryChanged({
            ...query,
            from: state.page.from,
            until: state.page.until,
            cursor: state.page.nextCursor,
          });
      }}
    />
  );
}
