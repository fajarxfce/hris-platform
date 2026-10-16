import type { AccountCredentialKind } from "../../features/identity/domain/entities/account-credential";
export function accountCredentialRoute(
  pathname: string,
): AccountCredentialKind | "requestRecovery" | null {
  if (pathname === "/auth/accept-invitation") return "invitation";
  if (pathname === "/auth/reset-password") return "passwordRecovery";
  if (pathname === "/auth/recover-password") return "requestRecovery";
  return null;
}
