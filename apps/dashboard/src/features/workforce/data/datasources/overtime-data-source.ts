import type { WorkforceEmployeePageDto } from "../models/calendar-dto";
import type { OvertimeActualDto } from "../models/overtime-actual-dto";
import type { OvertimeDecisionDto } from "../models/overtime-decision-dto";
import type { OvertimeDetailsDto } from "../models/overtime-details-dto";
import type { OvertimePlanDto } from "../models/overtime-plan-dto";
import type {
  OvertimePlanningReviewDto,
  OvertimePlanningSearchDto,
} from "../models/overtime-planning-dto";
import type { OvertimeReceiptDto } from "../models/overtime-receipt-dto";
import type { OvertimePageDto, OvertimeSearchDto } from "../models/overtime-request-dto";
import type { OvertimeWithdrawalDto } from "../models/overtime-withdrawal-dto";

export interface OvertimeDataSource {
  planningEmployees(
    company: string,
    search: OvertimePlanningSearchDto,
    signal: AbortSignal,
  ): Promise<WorkforceEmployeePageDto>;
  planningReview(
    company: string,
    employeeId: string,
    workDate: string,
    signal: AbortSignal,
  ): Promise<OvertimePlanningReviewDto>;
  plan(
    company: string,
    operation: string,
    body: OvertimePlanDto,
    signal: AbortSignal,
  ): Promise<OvertimeReceiptDto>;
  submitActual(
    company: string,
    id: string,
    operation: string,
    body: OvertimeActualDto,
    signal: AbortSignal,
  ): Promise<OvertimeReceiptDto>;
  decide(
    company: string,
    id: string,
    operation: string,
    body: OvertimeDecisionDto,
    signal: AbortSignal,
  ): Promise<OvertimeReceiptDto>;
  withdraw(
    company: string,
    id: string,
    operation: string,
    body: OvertimeWithdrawalDto,
    signal: AbortSignal,
  ): Promise<OvertimeReceiptDto>;
  list(company: string, search: OvertimeSearchDto, signal: AbortSignal): Promise<OvertimePageDto>;
  get(
    company: string,
    id: string,
    historyAfter: string | null,
    signal: AbortSignal,
  ): Promise<OvertimeDetailsDto>;
}
