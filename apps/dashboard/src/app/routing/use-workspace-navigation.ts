import { useCallback, useEffect, useLayoutEffect, useMemo, useSyncExternalStore } from "react";
import { type BlockerFunction, useBlocker } from "react-router-dom";
import { WorkspaceNavigationController } from "../../core/presentation/navigation/workspace-navigation-controller";

export function useWorkspaceNavigation(suspended: boolean) {
  const controller = useMemo(() => new WorkspaceNavigationController(), []);
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  const shouldBlock = useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) =>
      controller.getSnapshot().protection !== "none" &&
      (currentLocation.pathname !== nextLocation.pathname ||
        currentLocation.search !== nextLocation.search),
    [controller],
  );
  const blocker = useBlocker(shouldBlock);
  useLayoutEffect(() => controller.suspend(suspended), [controller, suspended]);
  useEffect(() => {
    if (blocker.state === "blocked") controller.request(blocker.proceed, blocker.reset);
  }, [blocker.state, blocker.proceed, blocker.reset, controller]);
  useEffect(() => () => controller.reset(), [controller]);
  useEffect(() => {
    if (state.protection === "none") return;
    const preventLoss = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", preventLoss);
    return () => window.removeEventListener("beforeunload", preventLoss);
  }, [state.protection]);
  return { controller, state };
}
