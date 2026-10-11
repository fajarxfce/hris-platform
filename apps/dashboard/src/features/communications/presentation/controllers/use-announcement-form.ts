import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { AudienceKind } from "../../domain/entities/announcement";
import type { AnnouncementEditorState } from "../models/announcement-editor-state";
import type {
  AnnouncementEditorController,
  AnnouncementFields,
} from "./announcement-editor-controller";

export function useAnnouncementForm(
  controller: AnnouncementEditorController,
  state: AnnouncementEditorState,
) {
  const values = useMemo<AnnouncementFields>(
    () => ({
      title: state.announcement?.title ?? "",
      body: state.announcement?.body ?? "",
      audienceKind: state.announcement?.audienceKind ?? "COMPANY",
      targetIds: state.announcement?.targetIds ?? [],
      acknowledgementRequired: state.announcement?.acknowledgementRequired ?? false,
      reason: "",
    }),
    [state.announcement],
  );
  const form = useForm<AnnouncementFields>({ values });
  const title = useController({ name: "title", control: form.control });
  const body = useController({ name: "body", control: form.control });
  const audienceKind = useController({ name: "audienceKind", control: form.control });
  const targetIds = useController({ name: "targetIds", control: form.control });
  const acknowledgementRequired = useController({
    name: "acknowledgementRequired",
    control: form.control,
  });
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
    title: title.field,
    body: body.field,
    audienceKind: audienceKind.field,
    targetIds: targetIds.field,
    acknowledgementRequired: acknowledgementRequired.field,
    reason: reason.field,
    editable: state.stage === "editing",
    changeKind: (next: AudienceKind) => {
      if (state.stage !== "editing" || next === audienceKind.field.value) return;
      audienceKind.field.onChange(next);
      targetIds.field.onChange([]);
    },
    submit: form.handleSubmit(controller.save),
    refresh: () =>
      depart(() => {
        void controller.refresh();
      }),
  };
}
