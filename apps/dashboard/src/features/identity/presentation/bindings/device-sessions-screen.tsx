import { useContext, useEffect, useMemo, useSyncExternalStore } from "react";
import type { AccountId } from "../../../../core/domain/identifiers";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { DeviceSessionUseCases } from "../contracts/device-session-use-cases";
import { DeviceSessionsController } from "../controllers/device-sessions-controller";
import { deviceSessionProperties, deviceSessionRows } from "../models/device-session-view";
import { DeviceSessionsPage } from "../pages/device-sessions-page";

export function DeviceSessionsScreen(props: {
  accountId: AccountId;
  actions: DeviceSessionUseCases;
  locale: Locale;
}) {
  return <DeviceSessionsBinding key={props.accountId} {...props} />;
}
function DeviceSessionsBinding({
  actions,
  locale,
}: {
  actions: DeviceSessionUseCases;
  locale: Locale;
}) {
  const visible = useContext(WorkspaceVisibilityContext);
  const controller = useMemo(() => new DeviceSessionsController(actions), [actions]);
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    if (visible) controller.activate();
    else controller.deactivate();
    return controller.deactivate;
  }, [controller, visible]);
  useWorkspaceRevalidation(state.failure);
  useNavigationProtection(
    state.stage === "revoking"
      ? "pending"
      : state.selected && state.stage !== "confirming"
        ? "unconfirmed"
        : "none",
  );
  const rows = useMemo(
    () => deviceSessionRows(state.sessions ?? [], locale),
    [state.sessions, locale],
  );
  const properties = useMemo(
    () => deviceSessionProperties(state.selected, locale),
    [state.selected, locale],
  );
  return (
    <DeviceSessionsPage
      state={state}
      rows={rows}
      properties={properties}
      locale={locale}
      visible={visible}
      onRefresh={controller.refresh}
      onReview={controller.review}
      onDismiss={controller.dismissReview}
      onEnd={controller.revoke}
    />
  );
}
