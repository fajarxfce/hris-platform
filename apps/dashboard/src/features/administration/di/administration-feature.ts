import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpAuditDataSource } from "../data/datasources/http-audit-data-source";
import { RemoteAuditRepository } from "../data/repositories/remote-audit-repository";
import { SearchAuditEvents } from "../domain/usecases/search-audit-events";

export function createAdministrationFeature(http: HttpClient) {
  const source = new HttpAuditDataSource(http);
  const audits = new RemoteAuditRepository(source);
  return { searchAudit: new SearchAuditEvents(audits) };
}
