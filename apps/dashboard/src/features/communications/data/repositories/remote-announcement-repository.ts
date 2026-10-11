import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { AnnouncementId } from "../../domain/entities/announcement";
import type { AnnouncementChange } from "../../domain/entities/announcement-change";
import type { AnnouncementRepository } from "../../domain/repositories/announcement-repository";
import type { AnnouncementDataSource } from "../datasources/announcement-data-source";
import { toAnnouncement, toAnnouncementPage } from "../mappers/announcement-mapper";

export class RemoteAnnouncementRepository implements AnnouncementRepository {
  constructor(private readonly source: AnnouncementDataSource) {}
  save(
    company: CompanyId,
    operation: OperationId,
    change: AnnouncementChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.save(
        company,
        change.id,
        operation,
        {
          expectedVersion: change.expectedVersion,
          title: change.title,
          body: change.body,
          audience: { kind: change.audienceKind, targetIds: [...change.targetIds] },
          acknowledgementRequired: change.acknowledgementRequired,
          reason: change.reason,
        },
        signal,
      );
      if (
        receipt.id.toLowerCase() !== change.id ||
        receipt.version !== (change.expectedVersion ?? -1) + 1
      )
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
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
