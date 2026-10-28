import { z } from "zod";

export const reportCatalogueDto = z.object({
  companyId: z.uuid(),
  evaluatedAt: z.iso.datetime(),
  reports: z
    .array(
      z.object({ kind: z.string().min(1).max(64), definitionVersion: z.string().min(1).max(64) }),
    )
    .max(7),
});
export type ReportCatalogueDto = z.infer<typeof reportCatalogueDto>;
