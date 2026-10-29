import type { AnnouncementId } from "./announcement";
export type AnnouncementDelivery = Readonly<{
  announcementId: AnnouncementId;
  recipientCount: number;
  pending: number;
  processing: number;
  completed: number;
  failed: number;
  superseded: number;
  unavailableRecipients: number;
  processedDevices: number;
  acceptedDevices: number;
  rejectedDevices: number;
  observedAt: string;
}>;
