import type { CompanyId } from "../../../../core/domain/identifiers";
import type { WorkPeriod } from "./work-period";
import type { WorkPeriodAttempt } from "./work-period-attempt";

export const workPeriodActions = ["close", "recover", "view_job", "view_snapshots"] as const;
export type WorkPeriodAction = (typeof workPeriodActions)[number];
export type WorkPeriodOverview = Readonly<{
  companyId: CompanyId;
  month: string;
  companyDate: string;
  companyTimezone: string;
  period: WorkPeriod | null;
  attempt: WorkPeriodAttempt | null;
  availableActions: readonly WorkPeriodAction[];
}>;
