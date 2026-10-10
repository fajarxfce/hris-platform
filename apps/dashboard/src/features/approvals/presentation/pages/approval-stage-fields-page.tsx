import { useRestoreFocusTarget } from "@fluentui/react-components";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { ApprovalKind } from "../../domain/entities/approval-request";
import { approvalAssignments } from "../../domain/entities/approval-template";
import { approvalActionPermissions } from "../../domain/policies/approval-template-policy";
import type { useApprovalStageForm } from "../controllers/use-approval-stage-form";
import { approvalMessages } from "../i18n/approval-messages";
import {
  approvalPermissionLabel,
  approvalTemplateMessages,
} from "../i18n/approval-template-messages";

export function ApprovalStageFieldsPage({
  form,
  index,
  count,
  kind,
  editable,
  locale,
  onRemove,
  onMove,
}: {
  form: ReturnType<typeof useApprovalStageForm>;
  index: number;
  count: number;
  kind: ApprovalKind;
  editable: boolean;
  locale: Locale;
  onRemove: (index: number) => void;
  onMove: (index: number, delta: -1 | 1) => void;
}) {
  const text = approvalTemplateMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <fieldset className="app-editor-section">
      <legend>
        {approvalMessages(locale).step} {index + 1}
      </legend>
      <div className="app-editor-form">
        <AppSelect
          label={text.assignment}
          {...form.assignment}
          disabled={!editable}
          onChange={(_event, data) => form.changeAssignment(data.value)}
        >
          {approvalAssignments.map((assignment) => (
            <option
              key={assignment}
              value={assignment}
              disabled={assignment === "MANAGER" && kind === "PAYROLL"}
            >
              {text[assignment]}
            </option>
          ))}
        </AppSelect>
        {form.assignment.value === "MANAGER" && <p>{text.managerHint}</p>}
        {form.assignment.value === "PERMISSION" && (
          <AppSelect label={text.permission} {...form.permission} disabled={!editable}>
            {approvalActionPermissions[kind].map((permission) => (
              <option key={permission} value={permission}>
                {approvalPermissionLabel(permission, locale)}
              </option>
            ))}
          </AppSelect>
        )}
        {form.assignment.value === "NAMED" && (
          <>
            <p>{text.namedHint}</p>
            <ul className="app-approval-accounts">
              {form.accounts.map((account) => (
                <li key={account.id}>
                  <span className="app-reference">{account.displayName}</span>
                  <AppButton
                    disabled={!editable}
                    onClick={() => form.remove(account.id)}
                    aria-label={`${text.removeApprover}: ${account.displayName}`}
                  >
                    {text.removeApprover}
                  </AppButton>
                </li>
              ))}
            </ul>
            <AppButton
              disabled={!editable || form.accounts.length >= 25}
              {...restore}
              onClick={form.openPicker}
            >
              {text.addApprover}
            </AppButton>
          </>
        )}
        <div className="app-form-actions">
          <AppButton disabled={!editable || index === 0} onClick={() => onMove(index, -1)}>
            {text.moveUp}
          </AppButton>
          <AppButton disabled={!editable || index === count - 1} onClick={() => onMove(index, 1)}>
            {text.moveDown}
          </AppButton>
          <AppButton disabled={!editable || count <= 1} onClick={() => onRemove(index)}>
            {text.removeStage}
          </AppButton>
        </div>
      </div>
    </fieldset>
  );
}
