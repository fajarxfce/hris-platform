import { z } from "zod";

export const reportFilterParametersDto = z.strictObject({
  asOf: z.string().max(10).nullable(),
  from: z.string().max(10).nullable(),
  until: z.string().max(10).nullable(),
  status: z.string().max(32).nullable(),
  companies: z.array(z.uuid()).max(32),
});
export const savedReportFilterDto = z.strictObject({
  id: z.uuid(),
  companyId: z.uuid(),
  name: z.string().min(1).max(80),
  kind: z.string().min(1).max(32),
  definitionVersion: z.string().min(1).max(64),
  parameters: reportFilterParametersDto,
  version: z.number().int().min(0).max(9999),
  createdAt: z.iso.datetime(),
  updatedAt: z.iso.datetime(),
});
export const savedReportFiltersDto = z.strictObject({
  companyId: z.uuid(),
  items: z.array(savedReportFilterDto).max(50),
});
export const reportFilterReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(10000),
});
export type SavedReportFilterDto = z.infer<typeof savedReportFilterDto>;
export type SavedReportFiltersDto = z.infer<typeof savedReportFiltersDto>;
export type ReportFilterReceiptDto = z.infer<typeof reportFilterReceiptDto>;
export type SaveReportFilterDto = Readonly<{
  name: string;
  kind: string;
  definitionVersion: string;
  parameters: z.infer<typeof reportFilterParametersDto>;
  expectedVersion: number | null;
}>;
