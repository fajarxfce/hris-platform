import { z } from "zod";

const count = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);

export const headcountReportDto = z.object({
  companyId: z.uuid(),
  asOf: z.iso.date(),
  evaluatedAt: z.iso.datetime(),
  definitionVersion: z.literal("headcount.v1"),
  employments: count,
  persons: count,
  active: count,
  probation: count,
  suspended: count,
  permanent: count,
  fixedTerm: count,
});

export type HeadcountReportDto = z.infer<typeof headcountReportDto>;
