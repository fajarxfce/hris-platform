import {
  DrawerBody,
  DrawerHeader,
  DrawerHeaderTitle,
  OverlayDrawer,
  useRestoreFocusSource,
  useRestoreFocusTarget,
} from "@fluentui/react-components";
import { Dismiss20Regular } from "@fluentui/react-icons";
import { type ReactNode, useContext } from "react";
import { WorkspaceVisibilityContext } from "../contracts/workspace-visibility";
import { AppButton } from "./app-button";

export function AppDetailsPanel({
  open,
  title,
  closeLabel,
  onClose,
  children,
}: {
  open: boolean;
  title: string;
  closeLabel: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const restoreFocus = useRestoreFocusSource();
  const restoreClose = useRestoreFocusTarget();
  const visible = useContext(WorkspaceVisibilityContext);
  return (
    <OverlayDrawer
      {...restoreFocus}
      open={open && visible}
      position="end"
      size="medium"
      style={{ width: "480px", maxWidth: "100%" }}
      onOpenChange={(_event, data) => {
        if (!data.open) onClose();
      }}
    >
      <DrawerHeader>
        <DrawerHeaderTitle
          action={
            <AppButton
              {...restoreClose}
              appearance="subtle"
              icon={<Dismiss20Regular />}
              aria-label={closeLabel}
              onClick={onClose}
            />
          }
        >
          {title}
        </DrawerHeaderTitle>
      </DrawerHeader>
      <DrawerBody>{children}</DrawerBody>
    </OverlayDrawer>
  );
}
