import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AnnouncementId } from "../../domain/entities/announcement";
import type { AnnouncementDelivery } from "../../domain/entities/announcement-delivery";
import type { AnnouncementDeliveryDto } from "../models/announcement-delivery-dto";
export function toAnnouncementDelivery(
  value: AnnouncementDeliveryDto,
  expected: AnnouncementId,
): AnnouncementDelivery {
  if (value.announcementId.toLowerCase() !== expected) throw new InvalidHttpResponseError();
  return Object.freeze({ ...value, announcementId: expected });
}
