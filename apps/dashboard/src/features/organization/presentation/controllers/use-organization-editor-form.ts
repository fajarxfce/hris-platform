import { useMemo, useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type {
  OrganizationUnit,
  OrganizationUnitKind,
} from "../../domain/entities/organization-unit";
import { parentUnitKinds } from "../../domain/policies/organization-change-policy";
import type { OrganizationEditorState } from "../models/organization-editor-state";
import type { OrganizationEditorController } from "./organization-editor-controller";

type Fields = {
  code: string;
  name: string;
  kind: OrganizationUnitKind;
  timezone: string;
  active: string;
  parent: OrganizationUnit | null;
};
export function useOrganizationEditorForm(
  controller: OrganizationEditorController,
  state: OrganizationEditorState,
  timezone: string,
) {
  const values = useMemo<Fields>(
    () => ({
      code: state.details?.unit.code ?? "",
      name: state.details?.unit.name ?? "",
      kind: state.details?.unit.kind ?? "DEPARTMENT",
      timezone: state.details?.unit.timezone ?? timezone,
      active: String(state.details?.unit.active ?? true),
      parent: state.details?.parent ?? null,
    }),
    [state.details, timezone],
  );
  const form = useForm<Fields>({ values });
  const code = useController({ name: "code", control: form.control });
  const name = useController({ name: "name", control: form.control });
  const kind = useController({ name: "kind", control: form.control });
  const zone = useController({ name: "timezone", control: form.control });
  const active = useController({ name: "active", control: form.control });
  const parent = useController({ name: "parent", control: form.control });
  const [choosingParent, chooseParent] = useReducer(
    (_previous: boolean, next: boolean) => next,
    false,
  );
  const requestDeparture = useNavigationProtection(
    state.stage === "saving"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  return {
    code: code.field,
    name: name.field,
    kind: kind.field,
    timezone: zone.field,
    active: active.field,
    parent: parent.field.value,
    choosingParent,
    chooseParent,
    parentKinds: parentUnitKinds[kind.field.value],
    selectParent: (unit: OrganizationUnit) => {
      form.setValue("parent", unit, { shouldDirty: true });
      chooseParent(false);
    },
    clearParent: () => form.setValue("parent", null, { shouldDirty: true }),
    editable: state.stage === "editing",
    dirty: form.formState.isDirty,
    refresh: () =>
      requestDeparture(() => {
        void controller.refresh();
      }),
    submit: form.handleSubmit((fields) =>
      controller.save({
        code: fields.code,
        name: fields.name,
        kind: fields.kind,
        timezone: fields.kind === "BRANCH" ? fields.timezone : null,
        active: fields.active === "true",
        parentId: fields.parent?.id ?? null,
      }),
    ),
  };
}
