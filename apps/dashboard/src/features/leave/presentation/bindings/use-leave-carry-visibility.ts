import { useContext, useEffect } from "react";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
/** Binds document/workspace visibility to the controller and owns its DOM listener. */
export function useLeaveCarryVisibility(controller: { setVisible: (visible: boolean) => void }) {
  const visible = useContext(WorkspaceVisibilityContext);
  useEffect(() => {
    const changed = () => controller.setVisible(visible && document.visibilityState !== "hidden");
    changed();
    document.addEventListener("visibilitychange", changed);
    return () => document.removeEventListener("visibilitychange", changed);
  }, [controller, visible]);
}
