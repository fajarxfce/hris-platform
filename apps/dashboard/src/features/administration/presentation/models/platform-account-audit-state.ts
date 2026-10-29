import type { Failure } from "../../../../core/domain/result";
import type { PlatformAccountAccessAudit } from "../../domain/entities/platform-account-access-audit";

export type PlatformAccountAuditState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  review: PlatformAccountAccessAudit | null;
  failure: Failure | null;
}>;
export const initialPlatformAccountAuditState: PlatformAccountAuditState = Object.freeze({
  stage: "idle",
  review: null,
  failure: null,
});
