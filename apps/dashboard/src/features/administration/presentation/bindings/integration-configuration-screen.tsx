import { useContext, useEffect, useMemo, useSyncExternalStore } from "react";
import { WorkspaceSessionContext } from "../../../../core/presentation/contracts/workspace-session";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PlatformAccess } from "../../../identity/domain/entities/platform-access";
import { canReadIntegrationConfiguration } from "../../domain/policies/integration-configuration-policy";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import { IntegrationConfigurationController } from "../controllers/integration-configuration-controller";
import { integrationConfigurationView } from "../models/integration-configuration-view";
import { IntegrationConfigurationPage } from "../pages/integration-configuration-page";

export function IntegrationConfigurationScreen(props: {
  access: PlatformAccess;
  load: AdministrationUseCases["loadIntegrationConfiguration"];
  locale: Locale;
}) {
  const visible = useContext(WorkspaceVisibilityContext);
  const accountId = props.access.accountId;
  const permitted = canReadIntegrationConfiguration(props.access);
  const access = useMemo(
    () =>
      Object.freeze({
        accountId,
        permissions: Object.freeze(permitted ? ["identity.manage"] : []),
      }),
    [accountId, permitted],
  );
  const controller = useMemo(
    () => new IntegrationConfigurationController(props.load, access),
    [props.load, access],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    if (visible) controller.activate();
    else controller.deactivate();
    return controller.deactivate;
  }, [controller, visible]);
  useWorkspaceRevalidation(state.failure);
  const revalidate = useContext(WorkspaceSessionContext)?.revalidate;
  useEffect(() => {
    if (permitted && state.failure?.code === "platform_administrator_required") revalidate?.();
  }, [state.failure, permitted, revalidate]);
  const view = useMemo(
    () => (state.review ? integrationConfigurationView(state.review, props.locale) : null),
    [state.review, props.locale],
  );
  return (
    <IntegrationConfigurationPage
      state={state}
      view={view}
      locale={props.locale}
      visible={visible}
      onRefresh={controller.refresh}
    />
  );
}
