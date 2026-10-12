import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { OvertimeActionState } from "../models/overtime-action-state";
import {
  type OvertimeActionValues,
  overtimeActionValues,
  parseOvertimeActualValues,
} from "../models/overtime-actual-values";
import type { OvertimeActionController } from "./overtime-action-controller";

export function useOvertimeActionForm(
  controller: OvertimeActionController,
  state: OvertimeActionState,
) {
  const values = useMemo(() => overtimeActionValues(state.review), [state.review]);
  const form = useForm<OvertimeActionValues>({ values });
  const startsAt = useController({ name: "startsAt", control: form.control });
  const endsAt = useController({ name: "endsAt", control: form.control });
  const breakMinutes = useController({ name: "breakMinutes", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const depart = useNavigationProtection(
    state.stage === "submitting"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  return {
    startsAt: startsAt.field,
    endsAt: endsAt.field,
    breakMinutes: breakMinutes.field,
    reason: reason.field,
    required: controller.action !== "APPROVE",
    editable: state.stage === "reviewing",
    invalidLocalTime: Boolean(form.formState.errors.root),
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
    submit: form.handleSubmit((fields) => {
      if (!state.review || state.stage !== "reviewing") return;
      if (controller.action === "SUBMIT_ACTUAL") {
        const actual = parseOvertimeActualValues(fields, state.review);
        if (actual === null) {
          form.setError("root", { type: "invalid_local_time" });
          return;
        }
        return controller.submitActual(actual, fields.reason);
      }
      return {
        APPROVE: controller.approve,
        REJECT: controller.reject,
        WITHDRAW: controller.withdraw,
      }[controller.action](fields.reason);
    }),
  };
}
