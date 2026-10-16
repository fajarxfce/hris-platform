import { useContext, useEffect, useMemo, useSyncExternalStore } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import { canManagePlatformAccounts } from "../../domain/policies/account-access-policy";
import type { ManagedAccountScreenProps } from "../contracts/managed-account-screen-props";
import { AccountDeviceSessionsController } from "../controllers/account-device-sessions-controller";
import { usePlatformAccountSession } from "../controllers/use-platform-account-session";
import { managedAccountMessages } from "../i18n/managed-account-messages";
import { deviceSessionProperties, deviceSessionRows } from "../models/device-session-view";
import { managedAccountContext } from "../models/managed-account-route";
import { DeviceSessionsPage } from "../pages/device-sessions-page";

export function AccountDeviceSessionsScreen(props: ManagedAccountScreenProps) {
  const { managedAccountId = "" } = useParams();
  const [parameters] = useSearchParams();
  return (
    <AccountDeviceSessionsBinding
      key={`${props.access.accountId}:${managedAccountId}`}
      {...props}
      accountId={managedAccountId}
      context={managedAccountContext(parameters)}
    />
  );
}

function AccountDeviceSessionsBinding(
  props: ManagedAccountScreenProps & { accountId: string; context: string },
) {
  const visible = useContext(WorkspaceVisibilityContext);
  const callerId = props.access.accountId;
  const administrator = canManagePlatformAccounts(props.access);
  // A refreshed permission array with identical authority must not restart this resource.
  const access = useMemo(
    () =>
      Object.freeze({
        accountId: callerId,
        permissions: Object.freeze(administrator ? ["identity.manage"] : []),
      }),
    [callerId, administrator],
  );
  const controller = useMemo(
    () => new AccountDeviceSessionsController(props.actions, access, props.accountId),
    [props.actions, access, props.accountId],
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
  }, [controller, visible]);
  usePlatformAccountSession(state.failure, access);
  useNavigationProtection(
    state.stage === "revoking"
      ? "pending"
      : state.selected && state.stage !== "confirming"
        ? "unconfirmed"
        : "none",
  );
  const rows = useMemo(
    () => deviceSessionRows(state.sessions ?? [], props.locale),
    [state.sessions, props.locale],
  );
  const properties = useMemo(
    () => deviceSessionProperties(state.selected, props.locale, state.account),
    [state.selected, state.account, props.locale],
  );
  const text = managedAccountMessages(props.locale);
  return (
    <DeviceSessionsPage
      state={state}
      rows={rows}
      properties={properties}
      locale={props.locale}
      visible={visible}
      context={state.account?.displayName ?? text.details}
      backLink={{
        to: `/platform/accounts/${encodeURIComponent(props.accountId)}${props.context ? `?${props.context}` : ""}`,
        label: text.backToAccount,
      }}
      onRefresh={controller.refresh}
      onReview={controller.review}
      onDismiss={controller.dismissReview}
      onEnd={controller.revoke}
    />
  );
}
