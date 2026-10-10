import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import { ClientPolicyController } from "../controllers/client-policy-controller";
import { useClientPolicyVersion } from "../controllers/use-client-policy-version";
import { clientPolicyView } from "../models/client-policy-view";
import { ClientPolicyPage } from "../pages/client-policy-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  loadClientPolicy: AdministrationUseCases["loadClientPolicy"];
  companyName: string;
  locale: Locale;
};

export function ClientPolicyScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const version =
    parameters.has("company") && parameters.get("company") !== props.access.companyId
      ? null
      : parameters.get("version");
  return (
    <ClientPolicyBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      version={version}
      onVersionChanged={(version) =>
        setParameters({ company: props.access.companyId, ...(version === null ? {} : { version }) })
      }
    />
  );
}

function ClientPolicyBinding({
  access,
  loadClientPolicy,
  version,
  locale,
  companyName,
  onVersionChanged,
}: Props & {
  version: string | null;
  onVersionChanged: (version: string | null) => void;
}) {
  const controller = useMemo(
    () => new ClientPolicyController(loadClientPolicy, access, version),
    [loadClientPolicy, access, version],
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
  const view = useMemo(
    () => (state.review ? clientPolicyView(state.review, locale) : null),
    [state.review, locale],
  );
  const history = useClientPolicyVersion(version, locale, onVersionChanged);
  return (
    <ClientPolicyPage
      state={state}
      view={view}
      history={history}
      locale={locale}
      companyName={companyName}
      onRefresh={controller.refresh}
      editTo={`/settings/client-policy/edit?${new URLSearchParams({ company: access.companyId })}`}
    />
  );
}
