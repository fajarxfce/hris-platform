import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { CompanyMembershipScreenProps } from "../contracts/company-membership-screen-props";
import { ManagedAccountsController } from "../controllers/managed-accounts-controller";
import { useManagedAccountSearch } from "../controllers/use-managed-account-search";
import { usePlatformAccountSession } from "../controllers/use-platform-account-session";
import { companyAccountCandidateRows } from "../models/company-account-candidate-view";
import { companyAccountContext } from "../models/company-account-route";
import { CompanyAccountPickerPage } from "../pages/company-account-picker-page";

export function CompanyAccountPickerScreen(props: CompanyMembershipScreenProps) {
  const [parameters] = useSearchParams();
  const company = parameters.get("company");
  if (company !== props.access.companyId)
    return <Navigate to={`/administration/members?company=${props.access.companyId}`} replace />;
  const query = parameters.get("query") ?? "";
  const after = parameters.get("after");
  return (
    <CompanyAccountPickerBinding
      key={`${props.accountId}:${company}:${query}:${after}`}
      {...props}
      query={query}
      after={after}
      context={companyAccountContext(props.access.companyId, parameters)}
    />
  );
}
function CompanyAccountPickerBinding(
  props: CompanyMembershipScreenProps & { query: string; after: string | null; context: string },
) {
  const navigate = useNavigate();
  const [, setParameters] = useSearchParams();
  const access = useMemo(
    () => Object.freeze({ company: props.access, platform: props.platform }),
    [props.access, props.platform],
  );
  const controller = useMemo(
    () =>
      new ManagedAccountsController(
        props.actions.loadCompanyAccountCandidates,
        access,
        props.query,
        props.after,
      ),
    [props.actions, access, props.query, props.after],
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
  usePlatformAccountSession(state.failure, props.platform);
  const search = useManagedAccountSearch(props.query, (query) =>
    setParameters({ company: props.access.companyId, ...(query ? { query } : {}) }),
  );
  const rows = useMemo(
    () => companyAccountCandidateRows(state.page?.items ?? [], props.accountId, props.locale),
    [state.page, props.accountId, props.locale],
  );
  return (
    <CompanyAccountPickerPage
      state={state}
      rows={rows}
      search={search}
      locale={props.locale}
      companyName={props.companyName}
      backTo={`/administration/members?company=${props.access.companyId}`}
      firstPage={props.after === null}
      hasQuery={props.query.length > 0}
      onRefresh={controller.refresh}
      onReset={() => setParameters({ company: props.access.companyId })}
      onFirst={() =>
        setParameters({
          company: props.access.companyId,
          ...(props.query ? { query: props.query } : {}),
        })
      }
      onNext={() => {
        if (state.page?.nextCursor)
          setParameters({
            company: props.access.companyId,
            query: props.query,
            after: state.page.nextCursor,
          });
      }}
      onSelect={(id) =>
        navigate(`/administration/members/new/${encodeURIComponent(id)}?${props.context}`)
      }
    />
  );
}
