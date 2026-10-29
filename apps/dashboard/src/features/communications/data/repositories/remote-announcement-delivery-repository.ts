import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { AnnouncementId } from "../../domain/entities/announcement";
import type { AnnouncementDeliveryRepository } from "../../domain/repositories/announcement-delivery-repository";
import type { AnnouncementDeliveryDataSource } from "../datasources/announcement-delivery-data-source";
import { toAnnouncementDelivery } from "../mappers/announcement-delivery-mapper";
export class RemoteAnnouncementDeliveryRepository implements AnnouncementDeliveryRepository {
  constructor(private readonly source: AnnouncementDeliveryDataSource) {}
  get(company: CompanyId, id: AnnouncementId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toAnnouncementDelivery(await this.source.get(company, id, signal), id),
    );
  }
}
