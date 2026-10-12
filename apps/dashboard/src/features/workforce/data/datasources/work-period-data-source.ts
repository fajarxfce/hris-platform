import type { WorkforceEmployeePageDto } from "../models/calendar-dto";
import type {
  WorkPeriodCommandDto,
  WorkPeriodEmployeeSearchDto,
  WorkPeriodReceiptDto,
} from "../models/work-period-command-dto";
import type { WorkPeriodOverviewDto } from "../models/work-period-overview-dto";
import type { WorkPeriodSnapshotDto } from "../models/work-period-snapshot-dto";

export interface WorkPeriodDataSource {
  overview(company: string, month: string, signal: AbortSignal): Promise<WorkPeriodOverviewDto>;
  employees(
    company: string,
    search: WorkPeriodEmployeeSearchDto,
    signal: AbortSignal,
  ): Promise<WorkforceEmployeePageDto>;
  snapshot(
    company: string,
    month: string,
    employee: string,
    signal: AbortSignal,
  ): Promise<WorkPeriodSnapshotDto>;
  close(
    company: string,
    month: string,
    operation: string,
    body: WorkPeriodCommandDto,
    signal: AbortSignal,
  ): Promise<WorkPeriodReceiptDto>;
  recover(
    company: string,
    month: string,
    operation: string,
    body: WorkPeriodCommandDto,
    signal: AbortSignal,
  ): Promise<WorkPeriodReceiptDto>;
}
