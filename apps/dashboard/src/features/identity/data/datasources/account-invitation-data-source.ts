import type { AccountInvitationDto, InvitationReceiptDto } from "../models/account-invitation-dto";
export interface AccountInvitationDataSource {
  invite(
    operation: string,
    input: AccountInvitationDto,
    signal: AbortSignal,
  ): Promise<InvitationReceiptDto>;
}
