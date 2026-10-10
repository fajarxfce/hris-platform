import { useMemo, useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import type { AccountId } from "../../../../core/domain/identifiers";
import { parseCompanyDateTime } from "../../../../core/presentation/dates/company-date-time";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { ApprovalAssignee } from "../../domain/entities/approval-assignee";
import type { ApprovalKind } from "../../domain/entities/approval-request";
import { isApprovalKind } from "../../domain/policies/approval-template-policy";
import type { ApprovalDelegationEditorState } from "../models/approval-delegation-editor-state";
import {
  type ApprovalDelegationFormValues,
  approvalDelegationFormValues,
} from "../models/approval-delegation-form-values";
import type { ApprovalDelegationEditorController } from "./approval-delegation-editor-controller";

type Picker = Readonly<{ field: "fromAccount" | "toAccount"; kind: ApprovalKind }> | null;
export function useApprovalDelegationForm(
  controller: ApprovalDelegationEditorController,
  state: ApprovalDelegationEditorState,
  account: AccountId,
  timezone: string,
  now: string,
  canChooseFrom: boolean,
) {
  const values = useMemo(
    () => approvalDelegationFormValues(state.delegation, account, timezone, now),
    [state.delegation, account, timezone, now],
  );
  const form = useForm<ApprovalDelegationFormValues>({ values });
  const kind = useController({ name: "kind", control: form.control });
  const fromAccount = useController({ name: "fromAccount", control: form.control });
  const toAccount = useController({ name: "toAccount", control: form.control });
  const validFrom = useController({ name: "validFrom", control: form.control });
  const validUntil = useController({ name: "validUntil", control: form.control });
  const active = useController({ name: "active", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const [picker, setPicker] = useReducer((_previous: Picker, next: Picker) => next, null);
  const editable = state.stage === "editing";
  const depart = useNavigationProtection(
    state.stage === "saving"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  return {
    kind: kind.field,
    fromAccount: fromAccount.field.value,
    toAccount: toAccount.field.value,
    validFrom: validFrom.field,
    validUntil: validUntil.field,
    invalidFrom: !!validFrom.fieldState.error,
    invalidUntil: !!validUntil.fieldState.error,
    active: active.field,
    reason: reason.field,
    editable,
    picker: editable && picker?.kind === kind.field.value ? picker : null,
    excluded: [fromAccount.field.value, ...(toAccount.field.value ? [toAccount.field.value] : [])],
    chooseFrom: () => {
      if (editable && canChooseFrom) setPicker({ field: "fromAccount", kind: kind.field.value });
    },
    chooseTo: () => {
      if (editable) setPicker({ field: "toAccount", kind: kind.field.value });
    },
    closePicker: () => setPicker(null),
    select: (selected: ApprovalAssignee) => {
      if (
        !editable ||
        !picker ||
        picker.kind !== kind.field.value ||
        (picker.field === "fromAccount" && !canChooseFrom)
      )
        return;
      if (
        selected.id ===
        form.getValues(picker.field === "fromAccount" ? "toAccount" : "fromAccount")?.id
      )
        return;
      form.setValue(picker.field, selected, { shouldDirty: true });
      setPicker(null);
    },
    changeKind: (value: string) => {
      if (!editable || !isApprovalKind(value) || value === kind.field.value) return;
      kind.field.onChange(value);
      toAccount.field.onChange(null);
      setPicker(null);
    },
    submit: form.handleSubmit((input) => {
      const from = parseCompanyDateTime(
        input.validFrom,
        timezone,
        state.delegation?.validFrom ?? null,
      );
      const until = parseCompanyDateTime(
        input.validUntil,
        timezone,
        state.delegation?.validUntil ?? null,
      );
      if (!from) form.setError("validFrom", { type: "validate" });
      if (!until) form.setError("validUntil", { type: "validate" });
      if (!from || !until) return;
      return controller.save({
        kind: input.kind,
        fromAccount: input.fromAccount.id,
        toAccount: input.toAccount?.id ?? ("" as AccountId),
        validFrom: from,
        validUntil: until,
        active: input.active,
        reason: input.reason,
      });
    }),
    refresh: () =>
      depart(() => {
        void controller.refresh();
      }),
  };
}
