import { useEffect } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { PersonAccountBindingState } from "../models/person-account-binding-state";
import type { PersonAccountBindingController } from "./person-account-binding-controller";

export function usePersonAccountBindingForm(
  controller: PersonAccountBindingController,
  state: PersonAccountBindingState,
) {
  const form = useForm({ defaultValues: { reason: "" } });
  const reset = form.reset;
  useEffect(() => reset(state.profile ? { reason: "" } : undefined), [state.profile, reset]);
  const reason = useController({ name: "reason", control: form.control });
  const depart = useNavigationProtection(
    state.stage === "submitting"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "bound" && (form.formState.isDirty || state.selected !== null)
          ? "dirty"
          : "none",
  );
  return {
    reason: reason.field,
    editable: state.stage === "editing" && !state.loadingCandidates,
    submit: form.handleSubmit((fields) => controller.bind(fields.reason)),
    refresh: () =>
      depart(() => {
        void controller.refresh();
      }),
  };
}
