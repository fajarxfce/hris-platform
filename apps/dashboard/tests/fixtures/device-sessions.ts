import type { DeviceSessionDto } from "../../src/features/identity/data/models/device-session-dto";

export function deviceSessionId(index = 1): string {
  return `34abc000-0000-4000-8000-${index.toString().padStart(12, "0")}`;
}
export function deviceSession(index = 1): DeviceSessionDto {
  return {
    id: deviceSessionId(index),
    deviceName: index === 1 ? "HRIS Android" : `HRIS device ${index}`,
    createdAt: "2026-10-01T09:00:00Z",
    expiresAt: "2026-10-31T09:00:00Z",
  };
}
