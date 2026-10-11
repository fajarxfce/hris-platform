import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { AudienceGroupEditorState } from "../models/audience-group-editor-state";
import type {
  AudienceGroupEditorController,
  AudienceGroupFields,
} from "./audience-group-editor-controller";

export function useAudienceGroupForm(
  controller: AudienceGroupEditorController,
  state: AudienceGroupEditorState,
) {
  const values = useMemo<AudienceGroupFields>(
    () => ({
      name: state.group?.name ?? "",
      active: state.group?.active ?? true,
      employmentIds: state.group?.employmentIds ?? [],
      reason: "",
    }),
    [state.group],
  );
  const form = useForm<AudienceGroupFields>({ values });
  const name = useController({ name: "name", control: form.control });
  const active = useController({ name: "active", control: form.control });
  const employmentIds = useController({ name: "employmentIds", control: form.control });
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
    name: name.field,
    active: active.field,
    employmentIds: employmentIds.field,
    reason: reason.field,
    editable: state.stage === "editing",
    submit: form.handleSubmit(controller.save),
    refresh: () =>
      depart(() => {
        void controller.refresh();
      }),
  };
}
