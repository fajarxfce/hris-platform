import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useController, useForm } from "react-hook-form";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalAssignee } from "../../domain/entities/approval-assignee";
import type { ApprovalKind } from "../../domain/entities/approval-request";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import { ApprovalAssigneePickerController } from "../controllers/approval-assignee-picker-controller";
import { ApprovalAssigneePickerPage } from "../pages/approval-assignee-picker-page";

export function ApprovalAssigneePicker({
  access,
  approvals,
  kind,
  selected,
  locale,
  onSelect,
  onDismiss,
}: {
  access: CompanyAccess;
  approvals: ApprovalsUseCases;
  kind: ApprovalKind;
  selected: readonly ApprovalAssignee[];
  locale: Locale;
  onSelect: (account: ApprovalAssignee) => void;
  onDismiss: () => void;
}) {
  const controller = useMemo(
    () => new ApprovalAssigneePickerController(approvals.loadAssignees, access, kind),
    [approvals.loadAssignees, access, kind],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useWorkspaceRevalidation(state.failure);
  const form = useForm({ defaultValues: { query: "" } });
  const query = useController({ name: "query", control: form.control });
  const rows = useMemo(
    () =>
      (state.page?.items ?? [])
        .filter((account) => !selected.some((value) => value.id === account.id))
        .map((account) => ({
          id: account.id,
          actionLabel: account.displayName,
          cells: [account.displayName, account.id],
        })),
    [state.page, selected],
  );
  return (
    <ApprovalAssigneePickerPage
      state={state}
      rows={rows}
      query={query.field}
      locale={locale}
      onSearch={(event) => {
        // Portal events still bubble through React's tree to the template form.
        event.stopPropagation();
        void form.handleSubmit((input) => controller.search(input.query))(event);
      }}
      onFirst={controller.firstPage}
      onNext={controller.nextPage}
      onRefresh={controller.refresh}
      onDismiss={onDismiss}
      onSelect={(id) => {
        const current = controller.getSnapshot();
        const account =
          current.stage === "ready"
            ? current.page?.items.find((item) => item.id === id)
            : undefined;
        if (account && !selected.some((item) => item.id === account.id)) onSelect(account);
      }}
    />
  );
}
