import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { OffboardingController } from "../controllers/offboarding-controller";
import { useOffboardingForm } from "../controllers/use-offboarding-form";
import { lifecycleCaseParameters, lifecycleCaseSearch } from "../models/lifecycle-case-route";
import { offboardingView } from "../models/offboarding-view";
import { OffboardingPage } from "../pages/offboarding-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  lifecycle: LifecycleUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
  nextIdentifier: () => string;
};
export function OffboardingScreen(props: Props) {
  const { caseId = "" } = useParams();
  const [parameters] = useSearchParams();
  const search = lifecycleCaseSearch(parameters, props.access.companyId);
  const canonical = lifecycleCaseParameters(props.access.companyId, search);
  const casePath = `/people/lifecycle/cases/${encodeURIComponent(caseId)}`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/people/lifecycle/cases?${canonical}`} replace />;
  if (!parameters.has("company") || !parameters.has("status"))
    return <Navigate to={`${casePath}/offboarding?${canonical}`} replace />;
  return (
    <OffboardingBinding
      key={`${props.accountId}:${props.access.companyId}:${caseId}`}
      {...props}
      id={caseId}
      backTo={`${casePath}?${canonical}`}
    />
  );
}
function OffboardingBinding(props: Props & { id: string; backTo: string }) {
  const controller = useMemo(
    () => new OffboardingController(props.lifecycle, props.access, props.id, props.nextIdentifier),
    [props.lifecycle, props.access, props.id, props.nextIdentifier],
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
  const form = useOffboardingForm(controller, state);
  useWorkspaceRevalidation(state.failure);
  const view = useMemo(
    () => (state.review ? offboardingView(state.review, props.locale, props.timezone) : null),
    [state.review, props.locale, props.timezone],
  );
  return (
    <OffboardingPage
      state={state}
      form={form}
      view={view}
      companyName={props.companyName}
      locale={props.locale}
      backTo={props.backTo}
      onRetry={controller.retry}
    />
  );
}
