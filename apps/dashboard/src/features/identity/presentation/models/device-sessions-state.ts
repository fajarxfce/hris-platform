import type { Failure } from "../../../../core/domain/result";
import type { DeviceSession } from "../../domain/entities/device-session";

export type DeviceSessionsState = Readonly<{
  stage: "loading" | "ready" | "confirming" | "revoking" | "unconfirmed" | "unavailable";
  sessions: readonly DeviceSession[] | null;
  selected: DeviceSession | null;
  failure: Failure | null;
  ended: boolean;
}>;
export const initialDeviceSessionsState: DeviceSessionsState = Object.freeze({
  stage: "loading",
  sessions: null,
  selected: null,
  failure: null,
  ended: false,
});
