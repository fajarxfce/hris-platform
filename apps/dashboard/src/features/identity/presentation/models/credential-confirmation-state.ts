import type { Failure } from "../../../../core/domain/result";
export type CredentialConfirmationState = Readonly<{
  stage: "editing" | "submitting" | "saved" | "unconfirmed" | "unavailable" | "signedOut";
  leaving: boolean;
  failure: Failure | null;
}>;
export const initialCredentialConfirmationState: CredentialConfirmationState = Object.freeze({
  stage: "unavailable",
  leaving: false,
  failure: { code: "credential_link_unavailable", fields: {}, parameters: {} },
});
