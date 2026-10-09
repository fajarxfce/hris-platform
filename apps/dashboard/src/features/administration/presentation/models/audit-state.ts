import type { Failure } from "../../../../core/domain/result";
import type { AuditEvent } from "../../domain/entities/audit-event";
import type { AuditPage } from "../../domain/entities/audit-page";

export type AuditState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  page: AuditPage | null;
  selected: AuditEvent | null;
  failure: Failure | null;
}>;

export const initialAuditState: AuditState = Object.freeze({
  stage: "idle",
  page: null,
  selected: null,
  failure: null,
});
