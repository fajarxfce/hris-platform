import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpAttendanceExceptionReportDataSource } from "../data/datasources/http-attendance-exception-report-data-source";
import { HttpHeadcountReportDataSource } from "../data/datasources/http-headcount-report-data-source";
import { RemoteAttendanceExceptionReportRepository } from "../data/repositories/remote-attendance-exception-report-repository";
import { RemoteHeadcountReportRepository } from "../data/repositories/remote-headcount-report-repository";
import { LoadAttendanceExceptionReport } from "../domain/usecases/load-attendance-exception-report";
import { LoadHeadcountReport } from "../domain/usecases/load-headcount-report";

export function createReportingFeature(http: HttpClient) {
  const source = new HttpHeadcountReportDataSource(http);
  const reports = new RemoteHeadcountReportRepository(source);
  const attendance = new RemoteAttendanceExceptionReportRepository(
    new HttpAttendanceExceptionReportDataSource(http),
  );
  return {
    loadHeadcount: new LoadHeadcountReport(reports),
    loadAttendanceExceptions: new LoadAttendanceExceptionReport(attendance),
  };
}
