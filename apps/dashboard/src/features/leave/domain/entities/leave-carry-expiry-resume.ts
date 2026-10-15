import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveCarryExpiryId } from "./leave-carry-expiry";
export type LeaveCarryExpiryResume = Readonly<{
  companyId: CompanyId;
  id: LeaveCarryExpiryId;
  expectedVersion: number;
  reason: string;
}>;
