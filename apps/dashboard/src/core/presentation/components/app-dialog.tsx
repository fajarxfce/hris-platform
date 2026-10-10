import {
  Dialog,
  DialogBody,
  DialogContent,
  DialogSurface,
  DialogTitle,
} from "@fluentui/react-components";
import { type ReactNode, useCallback, useLayoutEffect, useRef } from "react";

export function AppDialog({
  open,
  title,
  busy,
  onDismiss,
  children,
}: {
  open: boolean;
  title: string;
  busy: boolean;
  onDismiss?: (() => void) | undefined;
  children: ReactNode;
}) {
  const surface = useRef<HTMLDivElement>(null);
  const attachSurface = useCallback((element: HTMLDivElement | null) => {
    surface.current = element;
  }, []);
  useLayoutEffect(() => {
    const element = surface.current;
    // Pending reads can replace a focused control. Keep focus in the open dialog.
    if (open && element && element.ownerDocument.activeElement === element.ownerDocument.body)
      element.focus({ preventScroll: true });
  });
  return (
    <Dialog
      open={open}
      surfaceMotion={null}
      onOpenChange={(_event, data) => {
        if (!data.open && !busy) onDismiss?.();
      }}
    >
      <DialogSurface ref={attachSurface} aria-busy={busy} backdropMotion={null}>
        <DialogBody>
          <DialogTitle>{title}</DialogTitle>
          <DialogContent>{children}</DialogContent>
        </DialogBody>
      </DialogSurface>
    </Dialog>
  );
}
