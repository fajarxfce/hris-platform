import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { overtimeStatuses } from "../../../workforce/domain/entities/overtime-request";
import { reportDefinitions } from "../entities/report-catalogue";
import type { ReportSelection } from "../entities/report-selection";
import { isAttendanceClosingReportRange } from "./attendance-closing-report-policy";
import { isAttendanceReportRange } from "./attendance-report-policy";
import { isExpenseReportRange } from "./expense-report-policy";
import { isHeadcountCompanies, isHeadcountDate } from "./headcount-policy";
import { isLeaveReportRange } from "./leave-report-policy";
import { isOvertimeReportRange } from "./overtime-report-policy";
import { isPayrollCostReportRange } from "./payroll-cost-report-policy";

export function normalizeReportSelection(
  company: CompanyId,
  input: ReportSelection,
): Result<ReportSelection> {
  if (
    !Object.hasOwn(reportDefinitions, input.kind) ||
    reportDefinitions[input.kind] !== input.definitionVersion
  )
    return failed("unsupported_report_definition");
  const p = input.parameters;
  const invalid = () => failed("invalid_report_filter_parameters");
  if (
    p.companies.length > 32 ||
    [p.asOf, p.from, p.until].some((value) => value !== null && value.length > 10) ||
    (p.status !== null && p.status.length > 32)
  )
    return invalid();
  if (input.kind === "HEADCOUNT") {
    if (p.asOf === null || p.from !== null || p.until !== null || p.status !== null)
      return invalid();
    if (!isHeadcountDate(p.asOf)) return failed("invalid_report_date");
    const companies = p.companies.length ? p.companies : [company];
    if (!isHeadcountCompanies(companies)) return failed("invalid_report_companies");
    return success(
      Object.freeze({
        kind: input.kind,
        definitionVersion: input.definitionVersion,
        parameters: Object.freeze({
          ...p,
          companies: Object.freeze(companies.map((id) => id.toLowerCase() as CompanyId).sort()),
        }),
      }),
    );
  }
  if (
    p.asOf !== null ||
    p.companies.length ||
    p.from === null ||
    p.until === null ||
    (p.status !== null &&
      (input.kind !== "OVERTIME" || !overtimeStatuses.some((status) => status === p.status)))
  )
    return invalid();
  const valid = {
    ATTENDANCE_EXCEPTIONS: isAttendanceReportRange,
    ATTENDANCE_CLOSINGS: isAttendanceClosingReportRange,
    LEAVE: isLeaveReportRange,
    OVERTIME: isOvertimeReportRange,
    EXPENSES: isExpenseReportRange,
    PAYROLL_COSTS: isPayrollCostReportRange,
  }[input.kind](p.from, p.until);
  if (!valid) return invalid();
  return success(
    Object.freeze({
      kind: input.kind,
      definitionVersion: input.definitionVersion,
      parameters: Object.freeze({ ...p, companies: Object.freeze([]) }),
    }),
  );
}
