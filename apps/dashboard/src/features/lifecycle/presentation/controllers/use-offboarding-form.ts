import { useEffect } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import { validateOffboardingReview } from "../../domain/policies/offboarding-policy";
import type { OffboardingState } from "../models/offboarding-state";
import type { OffboardingController } from "./offboarding-controller";

export function useOffboardingForm(controller: OffboardingController, state: OffboardingState) {
  const form = useForm({ defaultValues: { reason: "" } });
  const reset = form.reset;
  useEffect(() => reset(state.review ? { reason: "" } : undefined), [state.review, reset]);
  const reason = useController({ name: "reason", control: form.control });
  const depart = useNavigationProtection(
    state.stage === "submitting"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "completed" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  const readiness = state.review ? validateOffboardingReview(state.review) : null;
  return {
    reason: reason.field,
    editable: state.stage === "reviewing" && readiness?.ok === true,
    blocker: readiness && !readiness.ok ? readiness.failure : null,
    submit: form.handleSubmit((fields) => controller.complete(fields.reason)),
    refresh: () =>
      depart(() => {
        void controller.refresh();
      }),
  };
}
