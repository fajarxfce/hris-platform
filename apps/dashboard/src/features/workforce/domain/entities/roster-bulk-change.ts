import type { CompanyId } from "../../../../core/domain/identifiers";
import type { RosterBulkSelection } from "./roster-bulk-selection";

export type RosterBulkChange = Readonly<{
  companyId: CompanyId;
  selection: RosterBulkSelection;
  reviewRevision: string;
  reason: string;
}>;
