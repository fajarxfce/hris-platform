import type { AnnouncementDeliveryDto } from "../data/models/announcement-delivery-dto";
import { announcementId } from "./communications-fixture";
export const deliveryDetail = (): AnnouncementDeliveryDto => ({
  announcementId,
  recipientCount: 8,
  pending: 1,
  processing: 1,
  completed: 2,
  failed: 1,
  superseded: 1,
  unavailableRecipients: 2,
  processedDevices: 7,
  acceptedDevices: 4,
  rejectedDevices: 2,
  email: null,
  observedAt: "2026-10-29T02:00:00Z",
});
