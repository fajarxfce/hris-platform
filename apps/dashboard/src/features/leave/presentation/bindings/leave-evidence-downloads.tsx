import { useEffect, useMemo, useSyncExternalStore } from "react";
import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveAttachmentSource } from "../../domain/entities/leave-attachment";
import { LeaveEvidencePanel } from "../components/leave-evidence-panel";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveAttachmentsController } from "../controllers/leave-attachments-controller";
import { leaveAttachmentRows } from "../models/leave-request-view";

export function LeaveEvidenceDownloads({
  download,
  access,
  request,
  locale,
  disabled = false,
  onUnavailable,
}: {
  download: LeaveUseCases["downloadAttachment"];
  access: CompanyAccess;
  request: LeaveAttachmentSource;
  locale: Locale;
  disabled?: boolean;
  onUnavailable: (observed: LeaveAttachmentSource, failure: Failure) => void;
}) {
  const controller = useMemo(
    () => new LeaveAttachmentsController(download, access, request),
    [download, access, request],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    if (!disabled) controller.activate();
    else controller.deactivate();
    return controller.deactivate;
  }, [controller, disabled]);
  useWorkspaceRevalidation(state.failure);
  useEffect(() => {
    if (state.stage === "unavailable" && state.failure) onUnavailable(request, state.failure);
  }, [state.stage, state.failure, request, onUnavailable]);
  const rows = useMemo(
    () => leaveAttachmentRows(request.attachments, locale),
    [request.attachments, locale],
  );
  return (
    <LeaveEvidencePanel
      rows={rows}
      state={state}
      locale={locale}
      disabled={disabled}
      onDownload={controller.open}
      onCancel={controller.cancel}
    />
  );
}
