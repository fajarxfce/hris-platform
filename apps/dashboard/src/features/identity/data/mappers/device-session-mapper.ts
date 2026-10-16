import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { DeviceSession, DeviceSessionId } from "../../domain/entities/device-session";
import type { DeviceSessionDto } from "../models/device-session-dto";

export function toDeviceSessions(values: readonly DeviceSessionDto[]): readonly DeviceSession[] {
  const items = values.map((value) => {
    if (
      value.deviceName.trim().length === 0 ||
      /\p{Cc}/u.test(value.deviceName) ||
      Date.parse(value.createdAt) >= Date.parse(value.expiresAt)
    )
      throw new InvalidHttpResponseError();
    return Object.freeze({
      id: value.id.toLowerCase() as DeviceSessionId,
      deviceName: value.deviceName,
      createdAt: value.createdAt,
      expiresAt: value.expiresAt,
    });
  });
  if (new Set(items.map((item) => item.id)).size !== items.length)
    throw new InvalidHttpResponseError();
  return Object.freeze(items);
}
