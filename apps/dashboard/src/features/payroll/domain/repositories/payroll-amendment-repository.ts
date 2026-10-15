import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type {
  PayrollAmendment,
  PayrollAmendmentDetails,
  PayrollAmendmentPage,
} from "../entities/payroll-amendment";
import type { PayrollAmendmentCalculation } from "../entities/payroll-amendment-calculation";
import type { PayrollAmendmentSearch } from "../entities/payroll-amendment-search";
export interface PayrollAmendmentRepository {
  list(
    company: CompanyId,
    search: PayrollAmendmentSearch,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentPage>>;
  get(
    company: CompanyId,
    id: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentDetails>>;
  history(
    company: CompanyId,
    id: string,
    after: number,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentPage>>;
  revision(
    company: CompanyId,
    id: string,
    version: number,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendment>>;
  calculation(
    company: CompanyId,
    id: string,
    calculationId: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentCalculation>>;
}
