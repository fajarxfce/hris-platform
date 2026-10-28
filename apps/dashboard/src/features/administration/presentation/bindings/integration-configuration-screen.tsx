import { useContext, useEffect, useMemo, useSyncExternalStore } from "react";
import { WorkspaceSessionContext } from "../../../../core/presentation/contracts/workspace-session";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PlatformAccess } from "../../../identity/domain/entities/platform-access";
import { canReadIntegrationConfiguration } from "../../domain/policies/integration-configuration-policy";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import { IntegrationConfigurationController } from "../controllers/integration-configuration-controller";
import { WorkerIntegrationsController } from "../controllers/worker-integrations-controller";
import { integrationConfigurationView } from "../models/integration-configuration-view";
import { workerIntegrationsView } from "../models/worker-integrations-view";
import { IntegrationConfigurationPage } from "../pages/integration-configuration-page";
import { WorkerIntegrationsPanel } from "../pages/worker-integrations-panel";

export function IntegrationConfigurationScreen(props: {
  access: PlatformAccess;
  load: AdministrationUseCases["loadIntegrationConfiguration"];
  loadWorkers: AdministrationUseCases["loadWorkerIntegrations"];
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
  const workers = useMemo(
    () => new WorkerIntegrationsController(props.loadWorkers, access),
    [props.loadWorkers, access],
  );
  const workerState = useSyncExternalStore(
    workers.subscribe,
    workers.getSnapshot,
    workers.getSnapshot,
  );
  useEffect(() => {
    if (visible) controller.activate();
    else controller.deactivate();
    if (visible && permitted) workers.activate();
    else workers.deactivate();
    return () => {
      controller.deactivate();
      workers.deactivate();
    };
  }, [controller, workers, visible, permitted]);
  useWorkspaceRevalidation(state.failure);
  useWorkspaceRevalidation(workerState.failure);
  const revalidate = useContext(WorkspaceSessionContext)?.revalidate;
  useEffect(() => {
    if (
      permitted &&
      (state.failure?.code === "platform_administrator_required" ||
        workerState.failure?.code === "platform_administrator_required")
    )
      revalidate?.();
  }, [state.failure, workerState.failure, permitted, revalidate]);
  const view = useMemo(
    () => (state.review ? integrationConfigurationView(state.review, props.locale) : null),
    [state.review, props.locale],
  );
  const workerView = useMemo(
    () => (workerState.review ? workerIntegrationsView(workerState.review, props.locale) : null),
    [workerState.review, props.locale],
  );
  const refresh = () => {
    void controller.refresh();
    if (permitted) void workers.refresh();
  };
  return (
    <>
      <IntegrationConfigurationPage
        state={state}
        view={view}
        locale={props.locale}
        visible={visible}
        onRefresh={refresh}
      />
      {permitted && (
        <WorkerIntegrationsPanel state={workerState} view={workerView} locale={props.locale} />
      )}
    </>
  );
}
