import type { CompanyId } from "../../../../core/domain/identifiers";
import type { OvertimeRequest } from "./overtime-request";
import type { OvertimeSearch } from "./overtime-search";

export type OvertimePage = Readonly<{
  companyId: CompanyId;
  search: OvertimeSearch;
  items: readonly OvertimeRequest[];
  nextCursor: string | null;
}>;
