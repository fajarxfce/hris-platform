import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { MfaEnrollment } from "../../domain/entities/mfa";
import type { CompanyAccess, Session } from "../../domain/entities/session";
import type { IdentityProvider } from "../../domain/entities/sign-in";
import type { IdentityWorkspace } from "./identity-workspace";

export type IdentityState = Readonly<{
  stage: "loading" | "signedOut" | "challenge" | "recoveryCodes" | "ready" | "unavailable";
  session: Session | null;
  companyId: CompanyId | null;
  access: CompanyAccess | null;
  workspace: IdentityWorkspace | null;
  verification: "required" | "requested" | null;
  providers: readonly IdentityProvider[];
  enrollment: MfaEnrollment | null;
  recoveryCodes: readonly string[];
  busy: boolean;
  failure: Failure | null;
}>;

export const initialIdentityState: IdentityState = {
  stage: "loading",
  session: null,
  companyId: null,
  access: null,
  workspace: null,
  verification: null,
  providers: [],
  enrollment: null,
  recoveryCodes: [],
  busy: false,
  failure: null,
};
