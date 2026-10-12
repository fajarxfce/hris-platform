import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { OvertimeIntervalInput } from "./overtime-interval";
import type { OvertimeRequestId } from "./overtime-request";

export type OvertimePlan = Readonly<{
  id: OvertimeRequestId;
  employeeId: EmployeeId;
  expectedEmploymentVersion: number;
  workDate: string;
  requested: OvertimeIntervalInput;
  reason: string;
  planningRevision: string;
}>;
