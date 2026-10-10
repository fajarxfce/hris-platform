import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { leaveMessages } from "../i18n/leave-messages";
import type { LeaveAttachmentsState } from "../models/leave-attachments-state";
import type { leaveAttachmentRows } from "../models/leave-request-view";

export function LeaveEvidencePanel({
  rows,
  state,
  locale,
  disabled,
  onDownload,
  onCancel,
}: {
  rows: ReturnType<typeof leaveAttachmentRows>;
  state: LeaveAttachmentsState;
  locale: Locale;
  disabled: boolean;
  onDownload: (revision: string) => void;
  onCancel: () => void;
}) {
  const text = leaveMessages(locale);
  return (
    <div className="app-leave-evidence" aria-busy={state.stage === "downloading"}>
      <AppFailure failure={state.failure} locale={locale} />
      <AppResourceTable
        title={text.attachments}
        columns={[
          { id: "file", label: text.file },
          { id: "type", label: text.mediaType },
          { id: "size", label: text.bytes, numeric: true },
        ]}
        rows={rows}
        action={{
          label: text.download,
          onOpen: onDownload,
          disabled: disabled || state.stage === "downloading" || state.stage === "unavailable",
        }}
      />
      {state.stage === "downloading" && (
        <div className="app-form-actions">
          <AppLoading label={text.downloading} />
          <AppButton onClick={onCancel}>{text.cancelDownload}</AppButton>
        </div>
      )}
      {state.stage === "started" && <p role="status">{text.downloadStarted}</p>}
    </div>
  );
}
