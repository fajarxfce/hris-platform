import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpAttendanceExceptionReportDataSource } from "../data/datasources/http-attendance-exception-report-data-source";
import { HttpExpenseReportDataSource } from "../data/datasources/http-expense-report-data-source";
import { HttpHeadcountReportDataSource } from "../data/datasources/http-headcount-report-data-source";
import { HttpLeaveReportDataSource } from "../data/datasources/http-leave-report-data-source";
import { HttpOvertimeReportDataSource } from "../data/datasources/http-overtime-report-data-source";
import { RemoteAttendanceExceptionReportRepository } from "../data/repositories/remote-attendance-exception-report-repository";
import { RemoteExpenseReportRepository } from "../data/repositories/remote-expense-report-repository";
import { RemoteHeadcountReportRepository } from "../data/repositories/remote-headcount-report-repository";
import { RemoteLeaveReportRepository } from "../data/repositories/remote-leave-report-repository";
import { RemoteOvertimeReportRepository } from "../data/repositories/remote-overtime-report-repository";
import { LoadAttendanceExceptionReport } from "../domain/usecases/load-attendance-exception-report";
import { LoadExpenseReport } from "../domain/usecases/load-expense-report";
import { LoadExpenseReportClaims } from "../domain/usecases/load-expense-report-claims";
import { LoadHeadcountReport } from "../domain/usecases/load-headcount-report";
import { LoadLeaveReport } from "../domain/usecases/load-leave-report";
import { LoadLeaveReportRequests } from "../domain/usecases/load-leave-report-requests";
import { LoadOvertimeReport } from "../domain/usecases/load-overtime-report";

export function createReportingFeature(http: HttpClient) {
  const expenses = new RemoteExpenseReportRepository(new HttpExpenseReportDataSource(http));
  const leave = new RemoteLeaveReportRepository(new HttpLeaveReportDataSource(http));
  const source = new HttpHeadcountReportDataSource(http);
  const reports = new RemoteHeadcountReportRepository(source);
  const attendance = new RemoteAttendanceExceptionReportRepository(
    new HttpAttendanceExceptionReportDataSource(http),
  );
  return {
    loadExpenses: new LoadExpenseReport(expenses),
    loadExpenseClaims: new LoadExpenseReportClaims(expenses),
    loadOvertime: new LoadOvertimeReport(
      new RemoteOvertimeReportRepository(new HttpOvertimeReportDataSource(http)),
    ),
    loadLeave: new LoadLeaveReport(leave),
    loadLeaveRequests: new LoadLeaveReportRequests(leave),
    loadHeadcount: new LoadHeadcountReport(reports),
    loadAttendanceExceptions: new LoadAttendanceExceptionReport(attendance),
  };
}
