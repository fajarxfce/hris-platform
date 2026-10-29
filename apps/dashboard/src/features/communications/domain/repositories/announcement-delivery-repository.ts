import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { AnnouncementId } from "../entities/announcement";
import type { AnnouncementDelivery } from "../entities/announcement-delivery";
export interface AnnouncementDeliveryRepository {
  get(
    company: CompanyId,
    id: AnnouncementId,
    signal: AbortSignal,
  ): Promise<Result<AnnouncementDelivery>>;
}
