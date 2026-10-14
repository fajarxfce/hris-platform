import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { TaxOpeningCommandState } from "../models/tax-opening-command-state";
import type { TaxOpeningVerificationController } from "./tax-opening-verification-controller";
export function useTaxOpeningVerification(
  controller: TaxOpeningVerificationController,
  state: TaxOpeningCommandState,
) {
  const form = useForm({ defaultValues: { reason: "" } });
  const reason = useController({ control: form.control, name: "reason" }).field;
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
    reason,
    editable: state.stage === "editing",
    submit: form.handleSubmit((values) => controller.verify(values.reason)),
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
