import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import type { ManagedAccountScreenProps } from "../contracts/managed-account-screen-props";
import { ManagedAccountsController } from "../controllers/managed-accounts-controller";
import { useManagedAccountSearch } from "../controllers/use-managed-account-search";
import { usePlatformAccountSession } from "../controllers/use-platform-account-session";
import { managedAccountContext } from "../models/managed-account-route";
import { managedAccountRows } from "../models/managed-account-view";
import { ManagedAccountsPage } from "../pages/managed-accounts-page";

export function ManagedAccountsScreen(props: ManagedAccountScreenProps) {
  const [parameters] = useSearchParams();
  const query = parameters.get("query") ?? "";
  const after = parameters.get("after");
  return (
    <ManagedAccountsBinding
      key={`${props.access.accountId}:${query}:${after}`}
      {...props}
      query={query}
      after={after}
      context={managedAccountContext(parameters)}
    />
  );
}

function ManagedAccountsBinding(
  props: ManagedAccountScreenProps & { query: string; after: string | null; context: string },
) {
  const [, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const controller = useMemo(
    () =>
      new ManagedAccountsController(
        props.actions.loadManagedAccounts,
        props.access,
        props.query,
        props.after,
      ),
    [props.actions, props.access, props.query, props.after],
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
  const search = useManagedAccountSearch(props.query, (query) =>
    setParameters(query ? { query } : {}),
  );
  const rows = useMemo(
    () => managedAccountRows(state.page?.items ?? [], props.locale),
    [state.page, props.locale],
  );
  return (
    <ManagedAccountsPage
      state={state}
      rows={rows}
      search={search}
      locale={props.locale}
      inviteTo={`/platform/accounts/new${props.context ? `?${props.context}` : ""}`}
      firstPage={props.after === null}
      hasQuery={props.query.length > 0}
      onRefresh={controller.refresh}
      onReset={() => setParameters({})}
      onFirst={() => setParameters(props.query ? { query: props.query } : {})}
      onNext={() => {
        if (state.page?.nextCursor)
          setParameters({ query: props.query, after: state.page.nextCursor });
      }}
      onOpen={(id) =>
        navigate(
          `/platform/accounts/${encodeURIComponent(id)}${props.context ? `?${props.context}` : ""}`,
        )
      }
    />
  );
}
