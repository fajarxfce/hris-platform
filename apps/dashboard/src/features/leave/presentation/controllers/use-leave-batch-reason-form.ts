import { useEffect } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
export function useLeaveBatchReasonForm(
  controller: { save: (reason: string) => Promise<void>; refresh: () => Promise<void> },
  state: Readonly<{
    stage:
      | "loading"
      | "unavailable"
      | "reviewing"
      | "saving"
      | "unconfirmed"
      | "conflict"
      | "saved";
    review: unknown | null;
  }>,
) {
  const form = useForm({ defaultValues: { reason: "" } });
  const reset = form.reset;
  useEffect(() => {
    if (state.review === null) reset({ reason: "" });
  }, [state.review, reset]);
  const reason = useController({ name: "reason", control: form.control });
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
    reason: reason.field,
    submit: form.handleSubmit((fields) => controller.save(fields.reason)),
    reload: () =>
      depart(() => {
        reset({ reason: "" });
        void controller.refresh();
      }),
  };
}
