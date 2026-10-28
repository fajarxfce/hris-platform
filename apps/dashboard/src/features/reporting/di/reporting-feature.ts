import type { HttpClient } from "../../../core/data/http/http-client";
import type { FileRepository } from "../../../core/domain/files/file-repository";
import { HttpAttendanceClosingReportDataSource } from "../data/datasources/http-attendance-closing-report-data-source";
import { HttpAttendanceExceptionReportDataSource } from "../data/datasources/http-attendance-exception-report-data-source";
import { HttpExpenseReportDataSource } from "../data/datasources/http-expense-report-data-source";
import { HttpHeadcountReportDataSource } from "../data/datasources/http-headcount-report-data-source";
import { HttpLeaveReportDataSource } from "../data/datasources/http-leave-report-data-source";
import { HttpOvertimeReportDataSource } from "../data/datasources/http-overtime-report-data-source";
import { HttpPayrollCostReportDataSource } from "../data/datasources/http-payroll-cost-report-data-source";
import { HttpReportCatalogueDataSource } from "../data/datasources/http-report-catalogue-data-source";
import { HttpReportExportDataSource } from "../data/datasources/http-report-export-data-source";
import { HttpSavedReportFilterDataSource } from "../data/datasources/http-saved-report-filter-data-source";
import { RemoteAttendanceClosingReportRepository } from "../data/repositories/remote-attendance-closing-report-repository";
import { RemoteAttendanceExceptionReportRepository } from "../data/repositories/remote-attendance-exception-report-repository";
import { RemoteExpenseReportRepository } from "../data/repositories/remote-expense-report-repository";
import { RemoteHeadcountReportRepository } from "../data/repositories/remote-headcount-report-repository";
import { RemoteLeaveReportRepository } from "../data/repositories/remote-leave-report-repository";
import { RemoteOvertimeReportRepository } from "../data/repositories/remote-overtime-report-repository";
import { RemotePayrollCostReportRepository } from "../data/repositories/remote-payroll-cost-report-repository";
import { RemoteReportCatalogueRepository } from "../data/repositories/remote-report-catalogue-repository";
import { RemoteReportExportRepository } from "../data/repositories/remote-report-export-repository";
import { RemoteSavedReportFilterRepository } from "../data/repositories/remote-saved-report-filter-repository";
import { DeleteReportFilter } from "../domain/usecases/delete-report-filter";
import { DownloadReportExport } from "../domain/usecases/download-report-export";
import { GetSavedReportFilter } from "../domain/usecases/get-saved-report-filter";
import { ListSavedReportFilters } from "../domain/usecases/list-saved-report-filters";
import { LoadAttendanceClosingEmployees } from "../domain/usecases/load-attendance-closing-employees";
import { LoadAttendanceClosingReport } from "../domain/usecases/load-attendance-closing-report";
import { LoadAttendanceExceptionReport } from "../domain/usecases/load-attendance-exception-report";
import { LoadExpenseReport } from "../domain/usecases/load-expense-report";
import { LoadExpenseReportClaims } from "../domain/usecases/load-expense-report-claims";
import { LoadHeadcountReport } from "../domain/usecases/load-headcount-report";
import { LoadLeaveReport } from "../domain/usecases/load-leave-report";
import { LoadLeaveReportRequests } from "../domain/usecases/load-leave-report-requests";
import { LoadOvertimeReport } from "../domain/usecases/load-overtime-report";
import { LoadPayrollCostReport } from "../domain/usecases/load-payroll-cost-report";
import { LoadReportCatalogue } from "../domain/usecases/load-report-catalogue";
import { ManageReportExports } from "../domain/usecases/manage-report-exports";
import { ReadReportExports } from "../domain/usecases/read-report-exports";
import { SaveReportFilter } from "../domain/usecases/save-report-filter";

export function createReportingFeature(
  http: HttpClient,
  files: Pick<FileRepository, "downloadBinary">,
) {
  const exports = new RemoteReportExportRepository(new HttpReportExportDataSource(http));
  const filters = new RemoteSavedReportFilterRepository(new HttpSavedReportFilterDataSource(http));
  const closings = new RemoteAttendanceClosingReportRepository(
    new HttpAttendanceClosingReportDataSource(http),
  );
  const expenses = new RemoteExpenseReportRepository(new HttpExpenseReportDataSource(http));
  const leave = new RemoteLeaveReportRepository(new HttpLeaveReportDataSource(http));
  const source = new HttpHeadcountReportDataSource(http);
  const reports = new RemoteHeadcountReportRepository(source);
  const attendance = new RemoteAttendanceExceptionReportRepository(
    new HttpAttendanceExceptionReportDataSource(http),
  );
  return {
    readExports: new ReadReportExports(exports),
    manageExports: new ManageReportExports(exports),
    downloadExport: new DownloadReportExport(exports, files),
    listSavedFilters: new ListSavedReportFilters(filters),
    getSavedFilter: new GetSavedReportFilter(filters),
    saveFilter: new SaveReportFilter(filters),
    deleteFilter: new DeleteReportFilter(filters),
    loadCatalogue: new LoadReportCatalogue(
      new RemoteReportCatalogueRepository(new HttpReportCatalogueDataSource(http)),
    ),
    loadAttendanceClosings: new LoadAttendanceClosingReport(closings),
    loadAttendanceClosingEmployees: new LoadAttendanceClosingEmployees(closings),
    loadPayrollCosts: new LoadPayrollCostReport(
      new RemotePayrollCostReportRepository(new HttpPayrollCostReportDataSource(http)),
    ),
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
