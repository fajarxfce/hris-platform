import type {
  EmployeeCreationDto,
  EmployeeCreationReceiptDto,
} from "../models/employee-creation-dto";
import type { EmployeeDto, EmployeePageDto } from "../models/employee-dto";
import type { EmployeeSearchDto } from "../models/employee-search-dto";
import type { EmploymentHistoryPageDto } from "../models/employment-revision-dto";

export interface EmployeeDataSource {
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
