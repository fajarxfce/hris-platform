import type { Failure } from "../../../../core/domain/result";
import type { PlatformAuditEvent } from "../../domain/entities/platform-audit-event";
import type { PlatformAuditPage } from "../../domain/entities/platform-audit-page";

export type PlatformAuditState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  page: PlatformAuditPage | null;
  selected: PlatformAuditEvent | null;
  failure: Failure | null;
}>;

export const initialPlatformAuditState: PlatformAuditState = Object.freeze({
  stage: "idle",
  page: null,
  selected: null,
  failure: null,
});
