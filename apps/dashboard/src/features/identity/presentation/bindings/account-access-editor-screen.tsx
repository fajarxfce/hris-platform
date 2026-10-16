import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import type { ManagedAccountScreenProps } from "../contracts/managed-account-screen-props";
import { AccountAccessEditorController } from "../controllers/account-access-editor-controller";
import { useAccountAccessForm } from "../controllers/use-account-access-form";
import { usePlatformAccountSession } from "../controllers/use-platform-account-session";
import { managedAccountContext } from "../models/managed-account-route";
import { AccountAccessEditorPage } from "../pages/account-access-editor-page";

export function AccountAccessEditorScreen(props: ManagedAccountScreenProps) {
  const { managedAccountId = "" } = useParams();
  const [parameters] = useSearchParams();
  return (
    <AccountAccessEditorBinding
      key={`${props.access.accountId}:${managedAccountId}`}
      {...props}
      id={managedAccountId}
      context={managedAccountContext(parameters)}
    />
  );
}

function AccountAccessEditorBinding(
  props: ManagedAccountScreenProps & { id: string; context: string },
) {
  const controller = useMemo(
    () =>
      new AccountAccessEditorController(
        props.actions,
        props.access,
        props.id,
        props.nextIdentifier,
      ),
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
  usePlatformAccountSession(
    state.failure,
    props.access,
    state.stage === "saved" && state.receipt?.id === props.access.accountId,
  );
  const form = useAccountAccessForm(controller, state, props.access.accountId, props.locale);
  const query = props.context ? `?${props.context}` : "";
  return (
    <AccountAccessEditorPage
      state={state}
      form={form}
      locale={props.locale}
      backTo={`/platform/accounts/${encodeURIComponent(props.id)}${query}`}
      savedTo={state.receipt ? `/platform/accounts/${state.receipt.id}${query}` : null}
      onRetry={controller.retrySave}
    />
  );
}
