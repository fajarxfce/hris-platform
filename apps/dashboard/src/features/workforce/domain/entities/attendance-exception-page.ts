import type { CompanyId } from "../../../../core/domain/identifiers";
import type { AttendanceException } from "./attendance-exception";
import type { AttendanceExceptionSearch } from "./attendance-exception-search";

export type AttendanceExceptionPage = Readonly<{
  companyId: CompanyId;
  search: AttendanceExceptionSearch;
  items: readonly AttendanceException[];
  nextCursor: string | null;
}>;
