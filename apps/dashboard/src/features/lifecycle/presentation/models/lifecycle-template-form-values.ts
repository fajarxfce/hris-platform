import type { LifecycleKind, LifecycleTemplate } from "../../domain/entities/lifecycle-template";
import type { LifecycleTemplateFields } from "../controllers/lifecycle-template-editor-controller";

export type LifecycleTemplateFormValues = {
  code: string;
  name: string;
  kind: LifecycleKind;
  active: boolean;
  reason: string;
  tasks: { key: string; title: string; required: boolean; dueDays: string }[];
};
export function lifecycleTemplateFormValues(
  template: LifecycleTemplate | null,
): LifecycleTemplateFormValues {
  return {
    code: template?.code ?? "",
    name: template?.name ?? "",
    kind: template?.kind ?? "ONBOARDING",
    active: template?.active ?? true,
    reason: "",
    tasks: template?.tasks.map((task) => ({ ...task, dueDays: String(task.dueDays) })) ?? [
      { key: "", title: "", required: true, dueDays: "0" },
    ],
  };
}
export function lifecycleTemplateFields(
  values: LifecycleTemplateFormValues,
): LifecycleTemplateFields {
  return {
    code: values.code,
    name: values.name,
    kind: values.kind,
    active: values.active,
    reason: values.reason,
    tasks: values.tasks.map((task) => ({
      key: task.key,
      title: task.title,
      required: task.required,
      dueDays: task.dueDays.trim() === "" ? Number.NaN : Number(task.dueDays),
    })),
  };
}
