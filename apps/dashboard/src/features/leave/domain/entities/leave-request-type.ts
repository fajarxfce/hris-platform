import type { CompanyId } from "../../../../core/domain/identifiers";

export type LeaveRequestType = Readonly<{
  companyId: CompanyId;
  asOf: string;
  id: string;
  code: string;
  name: string;
  allowPartialDays: boolean;
  attachmentRequired: boolean;
}>;
export type LeaveRequestTypes = Readonly<{
  items: readonly LeaveRequestType[];
  nextCursor: string | null;
}>;
