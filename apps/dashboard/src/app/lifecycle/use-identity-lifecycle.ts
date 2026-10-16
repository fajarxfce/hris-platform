import { useEffect } from "react";
import type { IdentityController } from "../../features/identity/presentation/controllers/identity-controller";

/** Reconnect and foreground revalidation share the controller's single owned request. */
export function useIdentityLifecycle(identity: IdentityController, enabled = true) {
  useEffect(() => {
    if (!enabled) return;
    const refresh = () => {
      const stage = identity.getSnapshot().stage;
      if (document.visibilityState === "visible" && (stage === "ready" || stage === "unavailable"))
        void identity.refreshSession();
    };
    identity.activate();
    document.addEventListener("visibilitychange", refresh);
    window.addEventListener("online", refresh);
    return () => {
      document.removeEventListener("visibilitychange", refresh);
      window.removeEventListener("online", refresh);
      identity.deactivate();
    };
  }, [identity, enabled]);
}
