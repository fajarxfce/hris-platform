import { useEffect } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { validateEmployeeImportReview } from "../../domain/policies/employee-import-review-policy";
import type { EmployeeImportTransitionState } from "../models/employee-import-transition-state";
import type { EmployeeImportTransitionController } from "./employee-import-transition-controller";

export function useEmployeeImportTransitionForm(
  controller: EmployeeImportTransitionController,
  state: EmployeeImportTransitionState,
  access: CompanyAccess,
) {
  const form = useForm({ defaultValues: { reason: "", allowPartial: false } });
  const reset = form.reset;
  useEffect(
    () => reset(state.review ? { reason: "", allowPartial: false } : undefined),
    [state.review, reset],
  );
  const reason = useController({ name: "reason", control: form.control });
  const partial = useController({ name: "allowPartial", control: form.control });
  const protection = useNavigationProtection(
    state.stage === "submitting"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  const readiness = state.review
    ? validateEmployeeImportReview(access, state.review, controller.action, partial.field.value)
    : null;
  const submit = {
    apply: form.handleSubmit((fields) => controller.apply(fields.reason, fields.allowPartial)),
    resume: form.handleSubmit((fields) => controller.resume(fields.reason)),
    cancel: form.handleSubmit((fields) => controller.cancel(fields.reason)),
  }[controller.action];
  return {
    reason: reason.field,
    partial: partial.field,
    submit,
    editable:
      state.stage === "reviewing" &&
      (readiness?.ok === true || readiness?.failure.code === "employee_import_has_invalid_rows"),
    ready: state.stage === "reviewing" && readiness?.ok === true,
    blocker: readiness && !readiness.ok ? readiness.failure : null,
    refresh: () =>
      protection(() => {
        void controller.refresh();
      }),
  };
}
