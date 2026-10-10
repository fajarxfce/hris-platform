import { createContext, useContext, useId, useLayoutEffect } from "react";
import type {
  NavigationProtection,
  WorkspaceNavigationController,
} from "./workspace-navigation-controller";

export const WorkspaceNavigationContext = createContext<WorkspaceNavigationController | null>(null);

/** Registers only presentation protection; form values remain owned by the feature. */
export function useNavigationProtection(protection: NavigationProtection) {
  const navigation = useContext(WorkspaceNavigationContext);
  const owner = useId();
  if (!navigation) throw new Error("An editor requires a workspace navigation owner");
  useLayoutEffect(() => {
    navigation.protect(owner, protection);
  }, [navigation, owner, protection]);
  useLayoutEffect(() => () => navigation.release(owner), [navigation, owner]);
  return navigation.request;
}
