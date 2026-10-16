import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import type { ManagedAccountScreenProps } from "../contracts/managed-account-screen-props";
import { AccountInvitationController } from "../controllers/account-invitation-controller";
import { useAccountInvitationForm } from "../controllers/use-account-invitation-form";
import { usePlatformAccountSession } from "../controllers/use-platform-account-session";
import { managedAccountContext } from "../models/managed-account-route";
import { AccountInvitationPage } from "../pages/account-invitation-page";
export function AccountInvitationScreen(props: ManagedAccountScreenProps) {
  const { managedAccountId } = useParams();
  const [parameters] = useSearchParams();
  return (
    <AccountInvitationBinding
      key={`${props.access.accountId}:${managedAccountId ?? "new"}`}
      {...props}
      id={managedAccountId ?? null}
      context={managedAccountContext(parameters)}
    />
  );
}
function AccountInvitationBinding(
  props: ManagedAccountScreenProps & { id: string | null; context: string },
) {
  const controller = useMemo(
    () =>
      new AccountInvitationController(props.actions, props.access, props.id, props.nextIdentifier),
    [props.actions, props.access, props.id, props.nextIdentifier],
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
  usePlatformAccountSession(state.failure, props.access);
  const form = useAccountInvitationForm(controller, state);
  const query = props.context ? `?${props.context}` : "";
  return (
    <AccountInvitationPage
      state={state}
      form={form}
      locale={props.locale}
      resend={props.id !== null}
      backTo={
        props.id === null
          ? `/platform/accounts${query}`
          : `/platform/accounts/${encodeURIComponent(props.id)}${query}`
      }
      savedTo={state.receipt ? `/platform/accounts/${state.receipt.id}${query}` : null}
      onRetry={controller.retrySave}
    />
  );
}
