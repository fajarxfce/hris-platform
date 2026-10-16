import { z } from "zod";

export const deviceSessionDto = z.object({
  id: z.uuid(),
  deviceName: z.string().min(1).max(100),
  createdAt: z.iso.datetime({ offset: true }).max(40),
  expiresAt: z.iso.datetime({ offset: true }).max(40),
});
// The server's configurable active-session limit cannot exceed fifty.
export const deviceSessionsDto = z.array(deviceSessionDto).max(50);
export type DeviceSessionDto = z.infer<typeof deviceSessionDto>;
