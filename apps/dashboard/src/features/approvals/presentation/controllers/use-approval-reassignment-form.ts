import { useMemo, useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { ApprovalAssignee } from "../../domain/entities/approval-assignee";
import { excludedApprovalAccounts } from "../../domain/policies/approval-reassignment-policy";
import type { ApprovalReassignmentState } from "../models/approval-reassignment-state";
import type { ApprovalReassignmentController } from "./approval-reassignment-controller";

type Values = { accounts: ApprovalAssignee[]; reason: string };
export function useApprovalReassignmentForm(
  controller: ApprovalReassignmentController,
  state: ApprovalReassignmentState,
) {
  const form = useForm<Values>({ defaultValues: { accounts: [], reason: "" } });
  const accounts = useController({ name: "accounts", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const [picker, setPicker] = useReducer((_previous: boolean, next: boolean) => next, false);
  const editable = state.stage === "editing";
  const excluded = useMemo(
    () => (state.request ? excludedApprovalAccounts(state.request) : []),
    [state.request],
  );
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
    accounts: accounts.field.value,
    reason: reason.field,
    editable,
    picker: picker && editable,
    excluded: [...excluded.map((id) => ({ id, displayName: id })), ...accounts.field.value],
    openPicker: () => {
      if (editable && accounts.field.value.length < 25) setPicker(true);
    },
    closePicker: () => setPicker(false),
    select: (account: ApprovalAssignee) => {
      const selected = form.getValues("accounts");
      if (
        !editable ||
        selected.length >= 25 ||
        excluded.includes(account.id) ||
        selected.some((item) => item.id === account.id)
      )
        return;
      accounts.field.onChange([...selected, account]);
      setPicker(false);
    },
    remove: (id: string) => {
      if (editable)
        accounts.field.onChange(form.getValues("accounts").filter((item) => item.id !== id));
    },
    submit: form.handleSubmit((input) =>
      controller.save({
        assignees: input.accounts.map((account) => account.id),
        reason: input.reason,
      }),
    ),
    refresh: () =>
      depart(() => {
        form.reset();
        setPicker(false);
        void controller.refresh();
      }),
  };
}
