import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { HeadcountReport } from "../entities/headcount-report";

export interface HeadcountReportRepository {
  load(
    companies: readonly CompanyId[],
    asOf: string,
    signal: AbortSignal,
  ): Promise<Result<HeadcountReport>>;
}
