import { type ComponentProps, useEffect, useMemo, useSyncExternalStore } from "react";
import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseSubmission } from "../../domain/entities/expense-submission";
import { ExpenseReceiptList } from "../components/expense-receipt-list";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import { ExpenseReceiptController } from "../controllers/expense-receipt-controller";

export function ExpenseEvidenceDownloads({
  download,
  access,
  submission,
  rows,
  locale,
  disabled = false,
  onUnavailable,
}: {
  download: ExpenseUseCases["downloadReceipt"];
  access: CompanyAccess;
  submission: ExpenseSubmission;
  rows: ComponentProps<typeof ExpenseReceiptList>["rows"];
  locale: Locale;
  disabled?: boolean;
  onUnavailable: (observed: ExpenseSubmission, failure: Failure) => void;
}) {
  const controller = useMemo(
    () => new ExpenseReceiptController(download, access, submission),
    [download, access, submission],
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
    if (state.stage === "unavailable" && state.failure) onUnavailable(submission, state.failure);
  }, [state.stage, state.failure, submission, onUnavailable]);
  return (
    <ExpenseReceiptList
      rows={rows}
      state={state}
      locale={locale}
      disabled={disabled}
      onDownload={controller.open}
      onCancel={controller.cancel}
    />
  );
}
