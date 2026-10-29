import { announcementId } from "./communications-fixture";
export const deliveryDetail = () => ({
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
  observedAt: "2026-10-29T02:00:00Z",
});
