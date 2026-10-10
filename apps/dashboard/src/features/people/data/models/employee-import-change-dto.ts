import { z } from "zod";

export type EmployeeImportChangeDto = Readonly<{ expectedVersion: number; reason: string }>;
export type EmployeeImportApplicationDto = EmployeeImportChangeDto &
  Readonly<{ allowPartial: boolean }>;
export const employeeImportReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export type EmployeeImportReceiptDto = z.infer<typeof employeeImportReceiptDto>;
