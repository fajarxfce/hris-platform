import type { CompanyId } from "../../../../core/domain/identifiers";
import type { WorkPeriodId } from "./work-period";

export type WorkPeriodIntent = "close" | "recover";
/** Minimal reviewed facts retained for an exact retry; no job or employee results. */
export type WorkPeriodActionContext = Readonly<{
  companyId: CompanyId;
  month: string;
  companyDate: string;
  periodId: WorkPeriodId | null;
  version: number;
  intent: WorkPeriodIntent;
}>;
