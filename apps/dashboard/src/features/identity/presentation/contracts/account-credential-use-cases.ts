import type { ConfirmAccountCredential } from "../../domain/usecases/confirm-account-credential";
import type { RequestPasswordRecovery } from "../../domain/usecases/request-password-recovery";
import type { SignOut } from "../../domain/usecases/sign-out";
export type AccountCredentialUseCases = Readonly<{
  confirmAccountCredential: Pick<ConfirmAccountCredential, "execute">;
  requestPasswordRecovery: Pick<RequestPasswordRecovery, "execute">;
  signOut: Pick<SignOut, "execute">;
}>;
