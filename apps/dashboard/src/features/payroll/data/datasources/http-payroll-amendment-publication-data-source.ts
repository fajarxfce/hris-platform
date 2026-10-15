import type { HttpClient } from "../../../../core/data/http/http-client";
import { payrollAmendmentReceiptDto } from "../models/payroll-amendment-command-dto";
import {
  type PayrollAmendmentPublicationRecoveryDto,
  type PayrollAmendmentPublicationStartDto,
  payrollAmendmentPublicationsDto,
} from "../models/payroll-amendment-publication-dto";
import type { PayrollAmendmentPublicationDataSource } from "./payroll-amendment-publication-data-source";

export class HttpPayrollAmendmentPublicationDataSource
  implements PayrollAmendmentPublicationDataSource
{
  constructor(private readonly http: HttpClient) {}
  async list(company: string, amendment: string, signal: AbortSignal) {
    return payrollAmendmentPublicationsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/amendments/${amendment}/publications` },
        signal,
      ),
    );
  }
  async start(
    company: string,
    amendment: string,
    id: string,
    operation: string,
    input: PayrollAmendmentPublicationStartDto,
    signal: AbortSignal,
  ) {
    return payrollAmendmentReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/amendments/${amendment}/publications/${id}`,
          method: "PUT",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
  async recover(
    company: string,
    amendment: string,
    id: string,
    operation: string,
    input: PayrollAmendmentPublicationRecoveryDto,
    signal: AbortSignal,
  ) {
    return payrollAmendmentReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/amendments/${amendment}/publications/${id}/recover`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
