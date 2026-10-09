import { z } from "zod";

export const clientModuleDto = z.enum([
  "PEOPLE",
  "WORKFORCE",
  "LEAVE",
  "EXPENSES",
  "PAYROLL",
  "DOCUMENTS",
  "COMMUNICATIONS",
  "REPORTING",
]);
export const clientBuildsDto = z.object({
  android: z.number().int().min(0).max(999_999_999),
  ios: z.number().int().min(0).max(999_999_999),
  web: z.number().int().min(0).max(999_999_999),
});
export const maintenanceWindowDto = z.object({
  startsAt: z.iso.datetime(),
  endsAt: z.iso.datetime(),
});
export type MaintenanceWindowDto = z.infer<typeof maintenanceWindowDto>;
