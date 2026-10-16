export interface AccountCredentialDataSource {
  recover(email: string, signal: AbortSignal): Promise<void>;
  acceptInvitation(token: string, password: string, signal: AbortSignal): Promise<void>;
  resetPassword(token: string, password: string, signal: AbortSignal): Promise<void>;
}
