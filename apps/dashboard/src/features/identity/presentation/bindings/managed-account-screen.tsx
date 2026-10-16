import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import type { ManagedAccountScreenProps } from "../contracts/managed-account-screen-props";
import { ManagedAccountController } from "../controllers/managed-account-controller";
import { usePlatformAccountSession } from "../controllers/use-platform-account-session";
import { managedAccountContext } from "../models/managed-account-route";
import { managedAccountProperties } from "../models/managed-account-view";
import { ManagedAccountPage } from "../pages/managed-account-page";

export function ManagedAccountScreen(props: ManagedAccountScreenProps) {
  const { managedAccountId = "" } = useParams();
  const [parameters] = useSearchParams();
  return (
    <ManagedAccountBinding
      key={`${props.access.accountId}:${managedAccountId}`}
      {...props}
      id={managedAccountId}
      context={managedAccountContext(parameters)}
    />
  );
}

function ManagedAccountBinding(props: ManagedAccountScreenProps & { id: string; context: string }) {
  const controller = useMemo(
    () => new ManagedAccountController(props.actions.loadManagedAccount, props.access, props.id),
    [props.actions, props.access, props.id],
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
  const properties = useMemo(
    () => (state.review ? managedAccountProperties(state.review.account, props.locale) : []),
    [state.review, props.locale],
  );
  const query = props.context ? `?${props.context}` : "";
  return (
    <ManagedAccountPage
      state={state}
      properties={properties}
      locale={props.locale}
      backTo={`/platform/accounts${query}`}
      editTo={`/platform/accounts/${encodeURIComponent(props.id)}/edit${query}`}
      onRefresh={controller.refresh}
    />
  );
}
