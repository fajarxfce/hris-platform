import type { CompanyId } from "../../../../core/domain/identifiers";

export type ShiftId = string & { readonly shiftId: unique symbol };
export const workModes = ["ONSITE", "REMOTE", "FIELD"] as const;
export type WorkMode = (typeof workModes)[number];
export type GeoFence = Readonly<{
  latitude: number;
  longitude: number;
  radiusMeters: number;
}>;
export type ShiftDetails = Readonly<{
  code: string;
  name: string;
  startsAt: string;
  endsAt: string;
  breakMinutes: number;
  timezone: string;
  mode: WorkMode;
  locationRequired: boolean;
  maxAccuracyMeters: number;
  fence: GeoFence | null;
}>;
export type ShiftDefinition = Readonly<{
  id: ShiftId;
  companyId: CompanyId;
  details: ShiftDetails;
  active: boolean;
  version: number;
}>;
