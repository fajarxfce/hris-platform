import { useContext, useEffect } from "react";
import type { Failure } from "../../../../core/domain/result";
import { WorkspaceSessionContext } from "../../../../core/presentation/contracts/workspace-session";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PlatformAccess } from "../../domain/entities/platform-access";
import { canManagePlatformAccounts } from "../../domain/policies/account-access-policy";

/** The application session owner handles revocation; account editors never own sign-in. */
export function usePlatformAccountSession(
  failure: Failure | null,
  access: PlatformAccess,
  selfAccessChanged = false,
): void {
  useWorkspaceRevalidation(failure);
  const revalidate = useContext(WorkspaceSessionContext)?.revalidate;
  const wasAdministrator = canManagePlatformAccounts(access);
  useEffect(() => {
    if (
      selfAccessChanged ||
      (wasAdministrator && failure?.code === "platform_administrator_required")
    )
      revalidate?.();
  }, [failure, wasAdministrator, selfAccessChanged, revalidate]);
}
