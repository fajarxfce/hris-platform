import { z } from "zod";
import { reportFilterParametersDto } from "./saved-report-filter-dto";
export const reportSelectionDto = z.strictObject({
  kind: z.string().min(1).max(32),
  definitionVersion: z.string().min(1).max(64),
  parameters: reportFilterParametersDto,
});
export const reportExportCursorDto = z.strictObject({ createdAt: z.iso.datetime(), id: z.uuid() });
export const reportExportDto = z.strictObject({
  id: z.uuid(),
  companyId: z.uuid(),
  selection: reportSelectionDto,
  dataset: z.enum(["SUMMARY", "DETAIL"]),
  status: z.enum(["QUEUED", "RENDERING", "READY", "STOPPED"]),
  version: z.number().int().min(0).max(9999),
  attempt: z.number().int().min(1).max(8),
  createdAt: z.iso.datetime(),
  updatedAt: z.iso.datetime(),
  expiresAt: z.iso.datetime(),
  retiredAt: z.iso.datetime().nullable(),
  expired: z.boolean(),
  completedRows: z.number().int().min(0).max(100000),
  bytes: z
    .number()
    .int()
    .min(0)
    .max(100 * 1024 * 1024),
  jobId: z.uuid(),
  jobStatus: z.enum(["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED"]),
  cancellationRequested: z.boolean(),
  failureCode: z
    .string()
    .regex(/^[a-z][a-z0-9_]{0,79}$/u)
    .nullable(),
});
export const reportExportHistoryDto = z.strictObject({
  items: z.array(reportExportDto).max(20),
  next: reportExportCursorDto.nullable(),
});
export const reportExportReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(9999),
});
export type ReportExportDto = z.infer<typeof reportExportDto>;
export type ReportExportHistoryDto = z.infer<typeof reportExportHistoryDto>;
export type ReportExportReceiptDto = z.infer<typeof reportExportReceiptDto>;
export type StartReportExportDto = z.infer<typeof reportSelectionDto> &
  Readonly<{ dataset: "SUMMARY" | "DETAIL" }>;
export type ReportExportCursorDto = z.infer<typeof reportExportCursorDto>;
