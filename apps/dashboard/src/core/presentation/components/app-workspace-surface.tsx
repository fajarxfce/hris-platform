import { PortalMountNodeProvider } from "@fluentui/react-components";
import { type ReactNode, useReducer } from "react";
import { WorkspaceVisibilityContext } from "../contracts/workspace-visibility";

/** Feature portals share the same hidden/inert boundary as their retained page. */
export function AppWorkspaceSurface({
  visible,
  children,
}: {
  visible: boolean;
  children: ReactNode;
}) {
  const [mount, attach] = useReducer(
    (_previous: HTMLDivElement | null, next: HTMLDivElement | null) => next,
    null,
  );
  return (
    <div ref={attach} hidden={!visible} inert={!visible}>
      {mount && (
        <PortalMountNodeProvider value={mount}>
          <WorkspaceVisibilityContext value={visible}>{children}</WorkspaceVisibilityContext>
        </PortalMountNodeProvider>
      )}
    </div>
  );
}
