import type { Failure } from "../../../../core/domain/result";
export type PasswordRecoveryState = Readonly<{
  stage: "editing" | "sending" | "sent";
  failure: Failure | null;
}>;
export const initialPasswordRecoveryState: PasswordRecoveryState = Object.freeze({
  stage: "editing",
  failure: null,
});
