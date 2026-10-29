import type { AnnouncementDeliveryDto } from "../models/announcement-delivery-dto";
export interface AnnouncementDeliveryDataSource {
  get(company: string, id: string, signal: AbortSignal): Promise<AnnouncementDeliveryDto>;
}
