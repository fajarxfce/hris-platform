import {
  Dialog,
  DialogActions,
  DialogBody,
  DialogContent,
  DialogSurface,
  DialogTitle,
  DialogTrigger,
} from "@fluentui/react-components";
import type { ReactElement } from "react";
import { AppButton } from "./app-button";

export function AppConfirmationDialog({
  open,
  title,
  message,
  confirmLabel,
  dismissLabel,
  busy,
  onConfirm,
  onDismiss,
  trigger,
}: {
  open: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  dismissLabel: string;
  busy: boolean;
  onConfirm: () => void;
  onDismiss: () => void;
  trigger: ReactElement;
}) {
  return (
    <Dialog
      open={open}
      onOpenChange={(_event, data) => {
        if (!data.open) onDismiss();
      }}
    >
      <DialogTrigger disableButtonEnhancement>{trigger}</DialogTrigger>
      <DialogSurface aria-busy={busy}>
        <DialogBody>
          <DialogTitle>{title}</DialogTitle>
          <DialogContent>{message}</DialogContent>
          <DialogActions>
            <AppButton onClick={onDismiss} disabled={busy}>
              {dismissLabel}
            </AppButton>
            <AppButton appearance="primary" onClick={onConfirm} disabled={busy}>
              {confirmLabel}
            </AppButton>
          </DialogActions>
        </DialogBody>
      </DialogSurface>
    </Dialog>
  );
}
