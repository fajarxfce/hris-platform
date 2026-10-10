import { useEffect, useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import type { SignInCredentials } from "../../domain/entities/sign-in";
import type { IdentityState } from "../models/identity-state";
import type { IdentityController } from "./identity-controller";
import { useMfaForm } from "./use-mfa-form";

export function useAuthForms(controller: IdentityController, stage: IdentityState["stage"]) {
  const signIn = useForm<SignInCredentials>({ defaultValues: { email: "", password: "" } });
  const mfa = useMfaForm(controller, stage);
  const emailField = useController({ control: signIn.control, name: "email" });
  const passwordField = useController({ control: signIn.control, name: "password" });
  const [passwordVisible, setPasswordVisible] = useReducer(
    (_value: boolean, next: boolean) => next,
    false,
  );
  const { reset: resetSignIn } = signIn;
  useEffect(() => {
    if (stage !== "signedOut") resetSignIn();
    setPasswordVisible(false);
  }, [stage, resetSignIn]);
  const submitSignIn = signIn.handleSubmit(async (value) => {
    await controller.signIn(value);
    signIn.resetField("password");
  });
  return {
    ...mfa,
    email: emailField.field,
    password: passwordField.field,
    submitSignIn,
    passwordVisible,
    togglePassword: () => setPasswordVisible(!passwordVisible),
  };
}

export type AuthForms = ReturnType<typeof useAuthForms>;
