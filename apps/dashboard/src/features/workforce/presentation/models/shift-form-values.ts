import type { ShiftDefinition, WorkMode } from "../../domain/entities/shift-definition";

export type ShiftFormValues = {
  code: string;
  name: string;
  startsAt: string;
  endsAt: string;
  breakMinutes: string;
  timezone: string;
  mode: WorkMode;
  active: string;
  locationRequired: string;
  maxAccuracyMeters: string;
  fenced: string;
  latitude: string;
  longitude: string;
  radiusMeters: string;
  reason: string;
};
export function shiftFormValues(shift: ShiftDefinition | null, timezone: string): ShiftFormValues {
  const details = shift?.details;
  return {
    code: details?.code ?? "",
    name: details?.name ?? "",
    startsAt: details?.startsAt ?? "08:00",
    endsAt: details?.endsAt ?? "17:00",
    breakMinutes: String(details?.breakMinutes ?? 60),
    timezone: details?.timezone ?? timezone,
    mode: details?.mode ?? "ONSITE",
    active: String(shift?.active ?? true),
    locationRequired: String(details?.locationRequired ?? false),
    maxAccuracyMeters: String(details?.maxAccuracyMeters ?? 100),
    fenced: String(details?.fence !== null && details?.fence !== undefined),
    latitude: details?.fence ? String(details.fence.latitude) : "",
    longitude: details?.fence ? String(details.fence.longitude) : "",
    radiusMeters: String(details?.fence?.radiusMeters ?? 100),
    reason: "",
  };
}
