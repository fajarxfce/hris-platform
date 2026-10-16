import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import { validOidcSubject } from "../../domain/policies/oidc-binding-policy";
import type { OidcBindingEditorState } from "../models/oidc-binding-editor-state";
import type {
  OidcBindingEditorController,
  OidcBindingFields,
} from "./oidc-binding-editor-controller";

export function useOidcBindingForm(
  controller: OidcBindingEditorController,
  state: OidcBindingEditorState,
) {
  const values = useMemo(
    () => ({
      issuer: state.review?.binding?.issuer ?? "",
      subject: state.review?.binding?.subject ?? "",
      active: state.review?.binding?.active ?? true,
      reason: "",
    }),
    [state.review],
  );
  const form = useForm<OidcBindingFields>({ values });
  const issuer = useController({
    name: "issuer",
    control: form.control,
    rules: { required: true, maxLength: 500 },
  });
  const subject = useController({
    name: "subject",
    control: form.control,
    rules: { required: true, maxLength: 255, validate: validOidcSubject },
  });
  const active = useController({ name: "active", control: form.control });
  const reason = useController({
    name: "reason",
    control: form.control,
    rules: { required: true, maxLength: 1000, validate: (value) => value.trim().length > 0 },
  });
  const depart = useNavigationProtection(
    state.stage === "saving"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  const editable = state.stage === "editing";
  const changed =
    state.review?.binding == null || active.field.value !== state.review.binding.active;
  return {
    issuer: issuer.field,
    issuerInvalid: issuer.fieldState.invalid,
    subject: subject.field,
    subjectInvalid: subject.fieldState.invalid,
    active: active.field,
    reason: reason.field,
    reasonInvalid: reason.fieldState.invalid,
    editable,
    identityReadOnly: !editable || state.review?.binding != null,
    activeDisabled: !editable || (state.review?.account.active === false && !active.field.value),
    canSave: editable && changed,
    changed,
    submit: form.handleSubmit((fields) => controller.save(fields)),
    refresh: () =>
      depart(() => {
        void controller.refresh();
      }),
  };
}
