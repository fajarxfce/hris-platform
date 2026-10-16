import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import type { ManagedAccountScreenProps } from "../contracts/managed-account-screen-props";
import { OidcBindingEditorController } from "../controllers/oidc-binding-editor-controller";
import { useOidcBindingForm } from "../controllers/use-oidc-binding-form";
import { usePlatformAccountSession } from "../controllers/use-platform-account-session";
import { oidcBindingContext } from "../models/oidc-binding-route";
import { oidcBindingProperties } from "../models/oidc-binding-view";
import { OidcBindingEditorPage } from "../pages/oidc-binding-editor-page";

export function OidcBindingEditorScreen(props: ManagedAccountScreenProps) {
  const { managedAccountId = "", oidcBindingId } = useParams();
  const [parameters] = useSearchParams();
  return (
    <OidcBindingEditorBinding
      key={`${props.access.accountId}:${managedAccountId}:${oidcBindingId ?? "new"}`}
      {...props}
      accountId={managedAccountId}
      bindingId={oidcBindingId ?? null}
      context={oidcBindingContext(parameters)}
    />
  );
}
function OidcBindingEditorBinding(
  props: ManagedAccountScreenProps & {
    accountId: string;
    bindingId: string | null;
    context: string;
  },
) {
  const controller = useMemo(
    () =>
      new OidcBindingEditorController(
        props.actions,
        props.access,
        props.accountId,
        props.bindingId,
        props.nextIdentifier,
      ),
    [props.actions, props.access, props.accountId, props.bindingId, props.nextIdentifier],
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
  const ownAccount = props.accountId.toLowerCase() === props.access.accountId;
  usePlatformAccountSession(state.failure, props.access, state.stage === "saved" && ownAccount);
  const form = useOidcBindingForm(controller, state);
  const properties = useMemo(
    () => (state.review?.binding ? oidcBindingProperties(state.review.binding, props.locale) : []),
    [state.review, props.locale],
  );
  const path = `/platform/accounts/${encodeURIComponent(props.accountId)}/sso`;
  const query = props.context ? `?${props.context}` : "";
  return (
    <OidcBindingEditorPage
      state={state}
      form={form}
      properties={properties}
      locale={props.locale}
      creating={props.bindingId === null}
      ownAccount={ownAccount}
      backTo={`${path}${query}`}
      savedTo={
        props.bindingId === null && state.receipt
          ? `${path}/${encodeURIComponent(state.receipt.id)}${query}`
          : null
      }
      onRetry={controller.retrySave}
    />
  );
}
