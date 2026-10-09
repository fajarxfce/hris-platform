import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpHeadcountReportDataSource } from "../data/datasources/http-headcount-report-data-source";
import { RemoteHeadcountReportRepository } from "../data/repositories/remote-headcount-report-repository";
import { LoadHeadcountReport } from "../domain/usecases/load-headcount-report";

export function createReportingFeature(http: HttpClient) {
  const source = new HttpHeadcountReportDataSource(http);
  const reports = new RemoteHeadcountReportRepository(source);
  return { loadHeadcount: new LoadHeadcountReport(reports) };
}
