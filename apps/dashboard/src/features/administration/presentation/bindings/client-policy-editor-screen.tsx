import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import { ClientPolicyEditorController } from "../controllers/client-policy-editor-controller";
import { useClientPolicyEditorForm } from "../controllers/use-client-policy-editor-form";
import { ClientPolicyEditorPage } from "../pages/client-policy-editor-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  administration: AdministrationUseCases;
  companyName: string;
  locale: Locale;
  clientBuild: number;
  nextIdentifier: () => string;
};
export function ClientPolicyEditorScreen(props: Props) {
  const [parameters] = useSearchParams();
  const query = new URLSearchParams({ company: props.access.companyId });
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/settings/client-policy?${query}`} replace />;
  if (!parameters.has("company"))
    return <Navigate to={`/settings/client-policy/edit?${query}`} replace />;
  return (
    <ClientPolicyEditorBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      backTo={`/settings/client-policy?${query}`}
    />
  );
}
function ClientPolicyEditorBinding(props: Props & { backTo: string }) {
  const controller = useMemo(
    () =>
      new ClientPolicyEditorController(props.administration, props.access, props.nextIdentifier),
    [props.administration, props.access, props.nextIdentifier],
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
  const form = useClientPolicyEditorForm(controller, state, props.clientBuild);
  useWorkspaceRevalidation(state.failure);
  return (
    <ClientPolicyEditorPage
      state={state}
      form={form}
      companyName={props.companyName}
      locale={props.locale}
      clientBuild={props.clientBuild}
      backTo={props.backTo}
      savedTo={state.receipt ? `${props.backTo}&version=${state.receipt.version}` : null}
      onRetry={controller.retrySave}
    />
  );
}
