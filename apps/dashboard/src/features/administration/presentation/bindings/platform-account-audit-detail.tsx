import { useContext, useEffect, useMemo, useSyncExternalStore } from "react";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PlatformAccess } from "../../../identity/domain/entities/platform-access";
import type { PlatformAuditEvent } from "../../domain/entities/platform-audit-event";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import { PlatformAccountAuditController } from "../controllers/platform-account-audit-controller";
import { PlatformAccountAuditPanel } from "../pages/platform-account-audit-panel";

export function PlatformAccountAuditDetail(props: {
  access: PlatformAccess;
  event: PlatformAuditEvent;
  load: AdministrationUseCases["getPlatformAccountAudit"];
  locale: Locale;
}) {
  const visible = useContext(WorkspaceVisibilityContext);
  const controller = useMemo(
    () => new PlatformAccountAuditController(props.load, props.access, props.event),
    [props.load, props.access, props.event],
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
  return (
    <PlatformAccountAuditPanel state={state} locale={props.locale} onRetry={controller.refresh} />
  );
}
