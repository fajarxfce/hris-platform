import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpAttendanceExceptionReportDataSource } from "../data/datasources/http-attendance-exception-report-data-source";
import { HttpHeadcountReportDataSource } from "../data/datasources/http-headcount-report-data-source";
import { HttpLeaveReportDataSource } from "../data/datasources/http-leave-report-data-source";
import { RemoteAttendanceExceptionReportRepository } from "../data/repositories/remote-attendance-exception-report-repository";
import { RemoteHeadcountReportRepository } from "../data/repositories/remote-headcount-report-repository";
import { RemoteLeaveReportRepository } from "../data/repositories/remote-leave-report-repository";
import { LoadAttendanceExceptionReport } from "../domain/usecases/load-attendance-exception-report";
import { LoadHeadcountReport } from "../domain/usecases/load-headcount-report";
import { LoadLeaveReport } from "../domain/usecases/load-leave-report";
import { LoadLeaveReportRequests } from "../domain/usecases/load-leave-report-requests";

export function createReportingFeature(http: HttpClient) {
  const leave = new RemoteLeaveReportRepository(new HttpLeaveReportDataSource(http));
  const source = new HttpHeadcountReportDataSource(http);
  const reports = new RemoteHeadcountReportRepository(source);
  const attendance = new RemoteAttendanceExceptionReportRepository(
    new HttpAttendanceExceptionReportDataSource(http),
  );
  return {
    loadLeave: new LoadLeaveReport(leave),
    loadLeaveRequests: new LoadLeaveReportRequests(leave),
    loadHeadcount: new LoadHeadcountReport(reports),
    loadAttendanceExceptions: new LoadAttendanceExceptionReport(attendance),
  };
}
