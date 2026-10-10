import { useContext, useEffect } from "react";
import type { Failure } from "../../domain/result";
import { WorkspaceSessionContext } from "../contracts/workspace-session";

/** Reports a failed scope to its application owner; it never retries the feature command. */
export function useWorkspaceRevalidation(failure: Failure | null): void {
  const revalidate = useContext(WorkspaceSessionContext)?.revalidate;
  useEffect(() => {
    if (
      revalidate &&
      failure &&
      [
        "authentication_required",
        "session_revoked",
        "unauthenticated",
        "company_access_denied",
      ].includes(failure.code)
    )
      revalidate();
  }, [failure, revalidate]);
}
