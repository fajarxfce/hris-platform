import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { OvertimeStatus } from "../../../workforce/domain/entities/overtime-request";
import type { OvertimeReport } from "../entities/overtime-report";

export interface OvertimeReportRepository {
  load(
    company: CompanyId,
    from: string,
    until: string,
    status: OvertimeStatus | null,
    signal: AbortSignal,
  ): Promise<Result<OvertimeReport>>;
}
