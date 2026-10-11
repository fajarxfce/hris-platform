import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { AnnouncementId } from "../../domain/entities/announcement";
import type { AnnouncementRepository } from "../../domain/repositories/announcement-repository";
import type { AnnouncementDataSource } from "../datasources/announcement-data-source";
import { toAnnouncement, toAnnouncementPage } from "../mappers/announcement-mapper";

export class RemoteAnnouncementRepository implements AnnouncementRepository {
  constructor(private readonly source: AnnouncementDataSource) {}
  list(company: CompanyId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toAnnouncementPage(await this.source.list(company, after, signal), company, after, null),
    );
  }
  get(company: CompanyId, id: AnnouncementId, version: number | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toAnnouncement(await this.source.get(company, id, version, signal), company, id, version),
    );
  }
  history(company: CompanyId, id: AnnouncementId, after: number | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toAnnouncementPage(
        await this.source.history(company, id, after, signal),
        company,
        after === null ? null : String(after),
        id,
      ),
    );
  }
}
