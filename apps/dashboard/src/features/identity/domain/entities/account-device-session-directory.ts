import type { DeviceSession } from "./device-session";
import type { ManagedAccount } from "./managed-account";

export type AccountDeviceSessionDirectory = Readonly<{
  account: ManagedAccount;
  sessions: readonly DeviceSession[];
}>;
