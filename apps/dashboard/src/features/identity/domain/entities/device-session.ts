export type DeviceSessionId = string & { readonly deviceSessionId: unique symbol };
export type DeviceSession = Readonly<{
  id: DeviceSessionId;
  deviceName: string;
  createdAt: string;
  expiresAt: string;
}>;
