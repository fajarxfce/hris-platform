import { useEffect, useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import type { SignInCredentials } from "../../domain/entities/sign-in";
import type { IdentityState } from "../models/identity-state";
import type { IdentityController } from "./identity-controller";

export function useAuthForms(controller: IdentityController, stage: IdentityState["stage"]) {
  const signIn = useForm<SignInCredentials>({ defaultValues: { email: "", password: "" } });
  const challenge = useForm<{ code: string }>({ defaultValues: { code: "" } });
  const emailField = useController({ control: signIn.control, name: "email" });
  const passwordField = useController({ control: signIn.control, name: "password" });
  const codeField = useController({ control: challenge.control, name: "code" });
  const [passwordVisible, setPasswordVisible] = useReducer(
    (_value: boolean, next: boolean) => next,
    false,
  );
  const [recovery, setRecovery] = useReducer((_value: boolean, next: boolean) => next, false);
  const { reset: resetSignIn } = signIn;
  const { reset: resetChallenge } = challenge;
  useEffect(() => {
    if (stage !== "signedOut") resetSignIn();
    if (stage !== "challenge") resetChallenge();
    setPasswordVisible(false);
    setRecovery(false);
  }, [stage, resetSignIn, resetChallenge]);
  const toggleRecovery = () => {
    challenge.reset();
    setRecovery(!recovery);
  };
  const submitSignIn = signIn.handleSubmit(async (value) => {
    await controller.signIn(value);
    signIn.resetField("password");
  });
  const submitChallenge = challenge.handleSubmit(async ({ code }) => {
    if (controller.getSnapshot().enrollment) await controller.confirmEnrollment(code);
    else await controller.verifyMfa(code, recovery);
    challenge.reset();
  });
  return {
    email: emailField.field,
    password: passwordField.field,
    code: codeField.field,
    submitSignIn,
    submitChallenge,
    passwordVisible,
    togglePassword: () => setPasswordVisible(!passwordVisible),
    recovery,
    toggleRecovery,
  };
}

export type AuthForms = ReturnType<typeof useAuthForms>;
