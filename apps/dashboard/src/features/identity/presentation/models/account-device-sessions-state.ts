import type { ManagedAccount } from "../../domain/entities/managed-account";
import { type DeviceSessionsState, initialDeviceSessionsState } from "./device-sessions-state";

export type AccountDeviceSessionsState = DeviceSessionsState &
  Readonly<{
    account: ManagedAccount | null;
  }>;
export const initialAccountDeviceSessionsState: AccountDeviceSessionsState = Object.freeze({
  ...initialDeviceSessionsState,
  account: null,
});
