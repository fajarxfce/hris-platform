import type { SearchAuditEvents } from "../../domain/usecases/search-audit-events";

export type AdministrationUseCases = Readonly<{ searchAudit: Pick<SearchAuditEvents, "execute"> }>;
