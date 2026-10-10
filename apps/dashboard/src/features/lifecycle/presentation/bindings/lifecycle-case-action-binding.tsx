import { useEffect, useMemo, useSyncExternalStore } from "react";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleCase } from "../../domain/entities/lifecycle-case";
import type { LifecycleCaseAction } from "../../domain/entities/lifecycle-case-change";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { LifecycleCaseActionController } from "../controllers/lifecycle-case-action-controller";
import { useLifecycleCaseActionForm } from "../controllers/use-lifecycle-case-action-form";
import { lifecycleCaseView } from "../models/lifecycle-case-view";
import { LifecycleCaseActionPage } from "../pages/lifecycle-case-action-page";

export function LifecycleCaseActionBinding(props: {
  access: CompanyAccess;
  actions: Pick<LifecycleUseCases, "cancelCase" | "completeOnboarding">;
  details: LifecycleCase;
  action: LifecycleCaseAction;
  locale: Locale;
  timezone: string;
  nextIdentifier: () => string;
  onClose: () => void;
  onReload: () => void;
}) {
  const change =
    props.action === "cancel" ? props.actions.cancelCase : props.actions.completeOnboarding;
  const controller = useMemo(
    () =>
      new LifecycleCaseActionController(
        change,
        props.access,
        props.details,
        props.action,
        props.nextIdentifier,
      ),
    [change, props.access, props.details, props.action, props.nextIdentifier],
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
  const form = useLifecycleCaseActionForm(controller, state, props.onClose, props.onReload);
  useWorkspaceRevalidation(state.failure);
  useEffect(() => {
    if (state.stage === "saved") props.onReload();
  }, [state.stage, props.onReload]);
  const properties = useMemo(
    () => lifecycleCaseView(props.details, props.locale, props.timezone).properties,
    [props.details, props.locale, props.timezone],
  );
  return (
    <LifecycleCaseActionPage
      action={props.action}
      properties={properties}
      state={state}
      form={form}
      locale={props.locale}
      onRetry={controller.retry}
    />
  );
}
