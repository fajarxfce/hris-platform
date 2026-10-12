import type { OvertimeStatus } from "./overtime-request";

export type OvertimeSearchInput = Readonly<{
  employeeId: string | null;
  from: string;
  until: string;
  status: string | null;
  after: string | null;
}>;
export type OvertimeSearch = Omit<OvertimeSearchInput, "status"> &
  Readonly<{ status: OvertimeStatus | null }>;
