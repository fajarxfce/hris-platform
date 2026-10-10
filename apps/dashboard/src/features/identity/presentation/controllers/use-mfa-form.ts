import { useEffect, useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import type { IdentityState } from "../models/identity-state";
import type { IdentityController } from "./identity-controller";

export function useMfaForm(controller: IdentityController, stage: IdentityState["stage"]) {
  const form = useForm<{ code: string }>({ defaultValues: { code: "" } });
  const code = useController({ control: form.control, name: "code" });
  const [recovery, setRecovery] = useReducer((_previous: boolean, next: boolean) => next, false);
  const { reset } = form;
  useEffect(() => {
    if (stage !== "challenge") reset();
    setRecovery(false);
  }, [stage, reset]);
  return {
    code: code.field,
    recovery,
    toggleRecovery: () => {
      form.reset();
      setRecovery(!recovery);
    },
    submitChallenge: form.handleSubmit(async ({ code }) => {
      if (controller.getSnapshot().enrollment) await controller.confirmEnrollment(code);
      else await controller.verifyMfa(code, recovery);
      form.reset();
    }),
  };
}

export type MfaForm = ReturnType<typeof useMfaForm>;
