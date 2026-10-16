import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type PayrollAmendmentProposalDto,
  payrollAmendmentPreviewDto,
} from "../models/payroll-amendment-dto";
import {
  type PayrollAmendmentSourceSearchDto,
  payrollAmendmentCandidatesDto,
  payrollAmendmentPreparationDto,
} from "../models/payroll-amendment-preparation-dto";
import type { PayrollAmendmentSourceDataSource } from "./payroll-amendment-source-data-source";
export class HttpPayrollAmendmentSourceDataSource implements PayrollAmendmentSourceDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(company: string, search: PayrollAmendmentSourceSearchDto, signal: AbortSignal) {
    const parameters = new URLSearchParams({
      year: String(search.year),
      query: search.query,
      limit: "50",
    });
    if (search.employeeId !== null) parameters.set("employeeId", search.employeeId);
    if (search.after !== null) parameters.set("after", search.after);
    return payrollAmendmentCandidatesDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/amendment-sources?${parameters}` },
        signal,
      ),
    );
  }
  async prepare(company: string, assessment: string, signal: AbortSignal) {
    return payrollAmendmentPreparationDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/payslips/${assessment}/amendment-preparation`,
        },
        signal,
      ),
    );
  }
  async preview(
    company: string,
    assessment: string,
    terms: PayrollAmendmentProposalDto,
    signal: AbortSignal,
  ) {
    return payrollAmendmentPreviewDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/payslips/${assessment}/amendment-preview`,
          method: "POST",
          body: terms,
        },
        signal,
      ),
    );
  }
}
