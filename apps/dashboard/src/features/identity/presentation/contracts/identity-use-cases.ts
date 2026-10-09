import type { BeginMfaEnrollment } from "../../domain/usecases/begin-mfa-enrollment";
import type { ConfirmMfaEnrollment } from "../../domain/usecases/confirm-mfa-enrollment";
import type { LoadCompanyAccess } from "../../domain/usecases/load-company-access";
import type { LoadIdentityProviders } from "../../domain/usecases/load-identity-providers";
import type { LoadSession } from "../../domain/usecases/load-session";
import type { SignIn } from "../../domain/usecases/sign-in";
import type { SignOut } from "../../domain/usecases/sign-out";
import type { VerifyMfa } from "../../domain/usecases/verify-mfa";

export type IdentityUseCases = Readonly<{
  loadSession: Pick<LoadSession, "execute">;
  loadCompanyAccess: Pick<LoadCompanyAccess, "execute">;
  loadProviders: Pick<LoadIdentityProviders, "execute">;
  signIn: Pick<SignIn, "execute">;
  signOut: Pick<SignOut, "execute">;
  beginEnrollment: Pick<BeginMfaEnrollment, "execute">;
  confirmEnrollment: Pick<ConfirmMfaEnrollment, "execute">;
  verifyMfa: Pick<VerifyMfa, "execute">;
}>;
