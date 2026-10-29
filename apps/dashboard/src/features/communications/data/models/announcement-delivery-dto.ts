import { z } from "zod";

const recipients = z.number().int().min(0).max(5000);
const devices = z.number().int().min(0).max(2_500_000);
export const announcementDeliveryDto = z
  .strictObject({
    announcementId: z.uuid(),
    recipientCount: recipients,
    pending: recipients,
    processing: recipients,
    completed: recipients,
    failed: recipients,
    superseded: recipients,
    unavailableRecipients: recipients,
    processedDevices: devices,
    acceptedDevices: devices,
    rejectedDevices: devices,
    observedAt: z.iso.datetime(),
  })
  .refine(
    (v) =>
      v.pending + v.processing + v.completed + v.failed + v.superseded + v.unavailableRecipients ===
        v.recipientCount &&
      v.processedDevices <= (v.recipientCount - v.unavailableRecipients) * 500 &&
      v.acceptedDevices + v.rejectedDevices <= v.processedDevices,
  );
export type AnnouncementDeliveryDto = z.infer<typeof announcementDeliveryDto>;
