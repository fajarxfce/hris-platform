import type { HttpClient } from "../../../../core/data/http/http-client";
import { employmentChangeReceiptDto } from "../models/employment-change-dto";
import {
  type EmploymentTransferInputDto,
  employmentTransferReviewDto,
  employmentTransfersDto,
} from "../models/employment-transfer-dto";
import type { EmploymentTransferDataSource } from "./employment-transfer-data-source";

export class HttpEmploymentTransferDataSource implements EmploymentTransferDataSource {
  constructor(private readonly http: HttpClient) {}
  async review(companyId: string, id: string, targetCompanyId: string, signal: AbortSignal) {
    const query = new URLSearchParams({ targetCompanyId });
    return employmentTransferReviewDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${companyId}/employees/${id}/transfer-review?${query}` },
        signal,
      ),
    );
  }
  async history(companyId: string, id: string, signal: AbortSignal) {
    return employmentTransfersDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${companyId}/employees/${id}/transfers` },
        signal,
      ),
    );
  }
  async transfer(
    companyId: string,
    id: string,
    operation: string,
    input: EmploymentTransferInputDto,
    signal: AbortSignal,
  ) {
    return employmentChangeReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${companyId}/employees/${id}/transfer`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
