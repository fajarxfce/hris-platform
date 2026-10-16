import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import type { ManagedAccountScreenProps } from "../contracts/managed-account-screen-props";
import { OidcBindingsController } from "../controllers/oidc-bindings-controller";
import { usePlatformAccountSession } from "../controllers/use-platform-account-session";
import { managedAccountContext } from "../models/managed-account-route";
import { oidcBindingContext } from "../models/oidc-binding-route";
import { oidcBindingRows } from "../models/oidc-binding-view";
import { OidcBindingsPage } from "../pages/oidc-bindings-page";

export function OidcBindingsScreen(props: ManagedAccountScreenProps) {
  const { managedAccountId = "" } = useParams();
  const [parameters] = useSearchParams();
  const after = parameters.get("bindingAfter");
  return (
    <OidcBindingsBinding
      key={`${props.access.accountId}:${managedAccountId}:${after}`}
      {...props}
      accountId={managedAccountId}
      after={after}
      accountContext={managedAccountContext(parameters)}
      context={oidcBindingContext(parameters)}
    />
  );
}
function OidcBindingsBinding(
  props: ManagedAccountScreenProps & {
    accountId: string;
    after: string | null;
    accountContext: string;
    context: string;
  },
) {
  const [, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const controller = useMemo(
    () =>
      new OidcBindingsController(
        props.actions.loadOidcBindings,
        props.access,
        props.accountId,
        props.after,
      ),
    [props.actions, props.access, props.accountId, props.after],
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
  const rows = useMemo(
    () => oidcBindingRows(state.directory?.page.items ?? [], props.locale),
    [state.directory, props.locale],
  );
  const accountPath = `/platform/accounts/${encodeURIComponent(props.accountId)}`;
  const query = props.context ? `?${props.context}` : "";
  return (
    <OidcBindingsPage
      state={state}
      rows={rows}
      locale={props.locale}
      backTo={`${accountPath}${props.accountContext ? `?${props.accountContext}` : ""}`}
      createTo={`${accountPath}/sso/new${query}`}
      firstPage={props.after === null}
      onRefresh={controller.refresh}
      onFirst={() => setParameters(props.accountContext)}
      onNext={() => {
        if (state.directory?.page.nextCursor) {
          const next = new URLSearchParams(props.accountContext);
          next.set("bindingAfter", state.directory.page.nextCursor);
          setParameters(next);
        }
      }}
      onOpen={(id) => navigate(`${accountPath}/sso/${encodeURIComponent(id)}${query}`)}
    />
  );
}
