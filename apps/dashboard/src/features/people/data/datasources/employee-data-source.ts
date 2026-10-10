import type {
  EmployeeCreationDto,
  EmployeeCreationReceiptDto,
} from "../models/employee-creation-dto";
import type { EmployeeDto, EmployeePageDto } from "../models/employee-dto";
import type { EmployeeSearchDto } from "../models/employee-search-dto";
import type { EmploymentCancellationDto } from "../models/employment-cancellation-dto";
import type {
  EmploymentChangeDto,
  EmploymentChangeReceiptDto,
} from "../models/employment-change-dto";
import type { EmploymentDetailsDto } from "../models/employment-details-dto";
import type { EmploymentRevisionDetailsDto } from "../models/employment-revision-details-dto";
import type { EmploymentHistoryPageDto } from "../models/employment-revision-dto";

export interface EmployeeDataSource {
  revision(
    companyId: string,
    id: string,
    revision: number,
    signal: AbortSignal,
  ): Promise<EmploymentRevisionDetailsDto>;
  cancelRevision(
    companyId: string,
    id: string,
    revision: number,
    operation: string,
    input: EmploymentCancellationDto,
    signal: AbortSignal,
  ): Promise<EmploymentChangeReceiptDto>;
  details(
    companyId: string,
    id: string,
    asOf: string,
    signal: AbortSignal,
  ): Promise<EmploymentDetailsDto>;
  revise(
    companyId: string,
    id: string,
    operation: string,
    change: EmploymentChangeDto,
    signal: AbortSignal,
  ): Promise<EmploymentChangeReceiptDto>;
  create(
    companyId: string,
    operation: string,
    input: EmployeeCreationDto,
    signal: AbortSignal,
  ): Promise<EmployeeCreationReceiptDto>;
  list(companyId: string, search: EmployeeSearchDto, signal: AbortSignal): Promise<EmployeePageDto>;
  get(companyId: string, id: string, asOf: string, signal: AbortSignal): Promise<EmployeeDto>;
  history(
    companyId: string,
    id: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<EmploymentHistoryPageDto>;
}
