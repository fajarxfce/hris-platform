import type { Locale } from "../i18n/messages";
import type { WorkspaceNavigationState } from "../navigation/workspace-navigation-controller";
import { AppButton } from "./app-button";
import { AppDialog } from "./app-dialog";

export function AppLeaveDialog({
  state,
  locale,
  onLeave,
  onStay,
}: {
  state: WorkspaceNavigationState;
  locale: Locale;
  onLeave: () => void;
  onStay: () => void;
}) {
  const id = locale === "id";
  return (
    <AppDialog
      open={state.deciding}
      busy={false}
      title={id ? "Tinggalkan halaman?" : "Leave this page?"}
      onDismiss={onStay}
    >
      <div className="app-form">
        <p>
          {state.protection === "pending" || state.protection === "unconfirmed"
            ? id
              ? "Hasil penyimpanan belum dipastikan. Perubahan mungkin sudah tersimpan. Jika keluar, periksa data sebelum mengirim perubahan baru."
              : "The save outcome is not confirmed. Changes may already be saved. If you leave, review the record before submitting another change."
            : id
              ? "Perubahan yang belum disimpan akan dihapus."
              : "Unsaved changes will be discarded."}
        </p>
        <div className="app-form-actions">
          <AppButton appearance="primary" onClick={onStay}>
            {id ? "Tetap di halaman" : "Stay on this page"}
          </AppButton>
          <AppButton onClick={onLeave}>{id ? "Tinggalkan halaman" : "Leave page"}</AppButton>
        </div>
      </div>
    </AppDialog>
  );
}
