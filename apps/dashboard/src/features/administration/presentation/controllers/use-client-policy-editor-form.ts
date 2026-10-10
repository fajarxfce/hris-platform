import { useEffect, useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import { type CompanyModule, companyModules } from "../../domain/entities/company-module";
import type { ClientPolicyEditorState } from "../models/client-policy-editor-state";
import {
  type ClientPolicyFormValues,
  clientPolicyChangeFromFields,
  clientPolicyFormValues,
} from "../models/client-policy-form-values";
import type { ClientPolicyEditorController } from "./client-policy-editor-controller";

export function useClientPolicyEditorForm(
  controller: ClientPolicyEditorController,
  state: ClientPolicyEditorState,
  clientBuild: number,
) {
  const values = useMemo(() => clientPolicyFormValues(state.settings), [state.settings]);
  const form = useForm<ClientPolicyFormValues>({ defaultValues: values });
  const reset = form.reset;
  useEffect(() => reset(values), [values, reset]);
  const activation = useController({ name: "activation", control: form.control });
  const activateAt = useController({ name: "activateAt", control: form.control });
  const disabledModules = useController({ name: "disabledModules", control: form.control });
  const android = useController({ name: "android", control: form.control });
  const ios = useController({ name: "ios", control: form.control });
  const web = useController({ name: "web", control: form.control });
  const maintenanceEnabled = useController({ name: "maintenanceEnabled", control: form.control });
  const maintenanceStarts = useController({ name: "maintenanceStarts", control: form.control });
  const maintenanceEnds = useController({ name: "maintenanceEnds", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const editable = state.stage === "editing" && state.settings?.latest?.version !== 9999;
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
    activation: activation.field,
    activateAt: activateAt.field,
    android: android.field,
    ios: ios.field,
    web: web.field,
    maintenanceEnabled: maintenanceEnabled.field,
    maintenanceStarts: maintenanceStarts.field,
    maintenanceEnds: maintenanceEnds.field,
    reason: reason.field,
    editable,
    webRequiresUpdate: Number(web.field.value) > clientBuild,
    modules: companyModules.map((key) => ({
      key,
      enabled: !disabledModules.field.value.includes(key),
    })),
    setModule: (key: CompanyModule, enabled: boolean) => {
      if (editable)
        disabledModules.field.onChange(
          companyModules.filter((module) =>
            module === key ? !enabled : disabledModules.field.value.includes(module),
          ),
        );
    },
    submit: form.handleSubmit((fields) => controller.save(clientPolicyChangeFromFields(fields))),
    refresh: () =>
      depart(() => {
        void controller.refresh();
      }),
  };
}
