import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { ExpensePreparationState } from "../models/expense-preparation-state";
import type { ExpensePreparationController } from "./expense-preparation-controller";
export function useExpensePreparationForm(
  controller: ExpensePreparationController,
  state: ExpensePreparationState,
) {
  const form = useForm({ defaultValues: { title: "", reason: "" } });
  const title = useController({
    name: "title",
    control: form.control,
    rules: { maxLength: 160, validate: (value) => value.trim().length > 0 },
  });
  const reason = useController({
    name: "reason",
    control: form.control,
    rules: { maxLength: 1000, validate: (value) => value.trim().length > 0 },
  });
  const depart = useNavigationProtection(
    state.stage === "submitting"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" &&
            (form.formState.isDirty ||
              state.items.length > 0 ||
              (state.panel.kind === "destination" && state.panel.dirty))
          ? "dirty"
          : "none",
  );
  return {
    title: title.field,
    reason: reason.field,
    titleInvalid: Boolean(title.fieldState.error),
    reasonInvalid: Boolean(reason.fieldState.error),
    submit: form.handleSubmit((fields) => controller.review(fields.title, fields.reason)),
    restart: () =>
      depart(() => {
        form.reset();
        controller.restart();
      }),
  };
}
