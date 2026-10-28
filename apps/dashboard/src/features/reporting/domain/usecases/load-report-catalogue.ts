import { failed, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadReportKind } from "../policies/report-catalogue-policy";
import type { ReportCatalogueRepository } from "../repositories/report-catalogue-repository";

export class LoadReportCatalogue {
  constructor(private readonly catalogue: ReportCatalogueRepository) {}
  async execute(access: CompanyAccess, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!access.permissions.includes("reports.read")) return failed("access_denied");
    const permissions = [...access.permissions];
    const result = await this.catalogue.load(access.companyId, signal);
    signal.throwIfAborted();
    if (!result.ok) return result;
    return success(
      Object.freeze({
        ...result.value,
        reports: Object.freeze(
          result.value.reports.filter((report) => canReadReportKind(permissions, report.kind)),
        ),
      }),
    );
  }
}
