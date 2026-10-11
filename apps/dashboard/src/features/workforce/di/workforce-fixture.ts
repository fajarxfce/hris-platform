import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { ShiftDto } from "../data/models/shift-dto";
import type { ShiftChange } from "../domain/entities/shift-change";
import type { ShiftDefinition, ShiftId } from "../domain/entities/shift-definition";

export const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
export const access = { companyId: company, permissions: ["workforce.read", "workforce.manage"] };
export const identifier = (n = 1) => `50000000-0000-4000-8000-${String(n).padStart(12, "0")}`;
export const operation = identifier(99) as OperationId;
export const signal = () => new AbortController().signal;
export const shiftDtoFixture = (n = 1, version = 0): ShiftDto => ({
  id: identifier(n),
  version,
  active: true,
  details: {
    code: `SHIFT_${String(n).padStart(3, "0")}`,
    name: `Shift ${n}`,
    startsAt: "22:00:00",
    endsAt: "06:00:00",
    breakMinutes: 30,
    timezone: "Asia/Jakarta",
    mode: "ONSITE",
    locationRequired: true,
    maxAccuracyMeters: 100,
    fence: { latitude: -6.2, longitude: 106.8, radiusMeters: 200 },
  },
});
export const shiftFixture = (n = 1, version = 0): ShiftDefinition => {
  const dto = shiftDtoFixture(n, version);
  return {
    ...dto,
    id: dto.id as ShiftId,
    companyId: company,
    details: { ...dto.details, startsAt: "22:00", endsAt: "06:00" },
  };
};
export const changeFixture = (): ShiftChange => ({
  id: identifier(),
  details: shiftFixture().details,
  active: true,
  expectedVersion: null,
  reason: "New coverage",
});
