import { z } from "zod";

export const employeeImportTemplateMaximumBytes = 16_384;
export const employeeImportTemplateDto = z.string().min(1).max(employeeImportTemplateMaximumBytes);
export type EmployeeImportStartDto = { id: string; fileName: string; csv: string; reason: string };
