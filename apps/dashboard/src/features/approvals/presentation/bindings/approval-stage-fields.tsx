import { memo } from "react";
import type { Control } from "react-hook-form";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalKind } from "../../domain/entities/approval-request";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import { useApprovalStageForm } from "../controllers/use-approval-stage-form";
import type { ApprovalTemplateFormValues } from "../models/approval-template-form-values";
import { ApprovalStageFieldsPage } from "../pages/approval-stage-fields-page";
import { ApprovalAssigneePicker } from "./approval-assignee-picker";

export const ApprovalStageFields = memo(function ApprovalStageFields({
  control,
  index,
  count,
  kind,
  editable,
  access,
  approvals,
  locale,
  onRemove,
  onMove,
}: {
  control: Control<ApprovalTemplateFormValues>;
  index: number;
  count: number;
  kind: ApprovalKind;
  editable: boolean;
  access: CompanyAccess;
  approvals: ApprovalsUseCases;
  locale: Locale;
  onRemove: (index: number) => void;
  onMove: (index: number, delta: -1 | 1) => void;
}) {
  const form = useApprovalStageForm(control, index, kind, editable);
  return (
    <>
      <ApprovalStageFieldsPage
        form={form}
        index={index}
        count={count}
        kind={kind}
        editable={editable}
        locale={locale}
        onRemove={onRemove}
        onMove={onMove}
      />
      {form.picker && (
        <ApprovalAssigneePicker
          key={kind}
          access={access}
          approvals={approvals}
          kind={kind}
          selected={form.accounts}
          locale={locale}
          onSelect={form.select}
          onDismiss={form.closePicker}
        />
      )}
    </>
  );
});
