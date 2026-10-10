import type {
  ApprovalDelegationChangeDto,
  ApprovalDelegationDto,
  ApprovalDelegationPageDto,
} from "../models/approval-delegation-dto";
import type { ApprovalAdministrationReceiptDto } from "../models/approval-template-dto";

export interface ApprovalDelegationDataSource {
  list(
    company: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<ApprovalDelegationPageDto>;
  get(company: string, id: string, signal: AbortSignal): Promise<ApprovalDelegationDto>;
  save(
    company: string,
    id: string,
    operation: string,
    change: ApprovalDelegationChangeDto,
    signal: AbortSignal,
  ): Promise<ApprovalAdministrationReceiptDto>;
}
