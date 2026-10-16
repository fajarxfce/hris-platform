import type { CompanyId } from "../../../../core/domain/identifiers";
import type { RosterBulkEmployee } from "./roster-bulk-employee";
import type { RosterBulkSelection } from "./roster-bulk-selection";

export type RosterBulkReview = Readonly<{
  companyId: CompanyId;
  selection: RosterBulkSelection;
  employees: readonly RosterBulkEmployee[];
  revision: string;
  changeCount: number;
}>;
