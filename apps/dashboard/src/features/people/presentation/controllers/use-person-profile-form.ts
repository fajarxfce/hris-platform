import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { PersonProfileState } from "../models/person-profile-state";
import type { PersonProfileController } from "./person-profile-controller";

type Fields = {
  legalName: string;
  birthDate: string;
  nationality: string;
  email: string;
  reason: string;
};
export function usePersonProfileForm(
  controller: PersonProfileController,
  state: PersonProfileState,
) {
  const values = useMemo<Fields>(
    () => ({
      legalName: state.profile?.legalName ?? "",
      birthDate: state.profile?.birthDate ?? "",
      nationality: state.profile?.nationality ?? "",
      email: state.profile?.email ?? "",
      reason: "",
    }),
    [state.profile],
  );
  const form = useForm<Fields>({ values });
  const legalName = useController({ name: "legalName", control: form.control });
  const birthDate = useController({ name: "birthDate", control: form.control });
  const nationality = useController({ name: "nationality", control: form.control });
  const email = useController({ name: "email", control: form.control });
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
    legalName: legalName.field,
    birthDate: birthDate.field,
    nationality: nationality.field,
    email: email.field,
    reason: reason.field,
    editable: state.stage === "editing",
    dirty: form.formState.isDirty,
    refresh: () =>
      depart(() => {
        void controller.refresh();
      }),
    submit: form.handleSubmit((fields) =>
      controller.save({
        ...fields,
        birthDate: fields.birthDate || null,
        email: fields.email || null,
      }),
    ),
  };
}
