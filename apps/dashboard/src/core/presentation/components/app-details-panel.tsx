import {
  DrawerBody,
  DrawerHeader,
  DrawerHeaderTitle,
  OverlayDrawer,
  useRestoreFocusSource,
} from "@fluentui/react-components";
import { Dismiss20Regular } from "@fluentui/react-icons";
import type { ReactNode } from "react";
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
  return (
    <OverlayDrawer
      {...restoreFocus}
      open={open}
      position="end"
      size="medium"
      style={{ width: "min(480px, 100vw)" }}
      onOpenChange={(_event, data) => {
        if (!data.open) onClose();
      }}
    >
      <DrawerHeader>
        <DrawerHeaderTitle
          action={
            <AppButton
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
