import { z } from "zod";

const count = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);

export const headcountCountsDto = z.object({
  employments: count,
  persons: count,
  active: count,
  probation: count,
  suspended: count,
  permanent: count,
  fixedTerm: count,
});

export const headcountReportDto = z.object({
  asOf: z.iso.date(),
  evaluatedAt: z.iso.datetime(),
  definitionVersion: z.literal("headcount.v1"),
  totals: headcountCountsDto,
  companies: z
    .array(z.object({ companyId: z.uuid(), counts: headcountCountsDto }))
    .min(1)
    .max(32),
});

export type HeadcountReportDto = z.infer<typeof headcountReportDto>;
export type HeadcountCountsDto = z.infer<typeof headcountCountsDto>;
