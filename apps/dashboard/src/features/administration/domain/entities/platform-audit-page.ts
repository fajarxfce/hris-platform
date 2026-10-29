import type { PlatformAuditEvent } from "./platform-audit-event";

export type PlatformAuditPage = Readonly<{
  from: string;
  until: string;
  evaluatedAt: string;
  items: readonly PlatformAuditEvent[];
  nextCursor: string | null;
}>;
