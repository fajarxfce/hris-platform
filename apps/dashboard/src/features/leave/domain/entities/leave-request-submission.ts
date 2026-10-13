import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveRequestSelection } from "./leave-request-selection";

export type LeaveRequestSubmission = Readonly<
  LeaveRequestSelection & {
    companyId: CompanyId;
    id: string;
    reason: string;
    attachmentRevisionIds: readonly string[];
    previewRevision: string;
  }
>;
