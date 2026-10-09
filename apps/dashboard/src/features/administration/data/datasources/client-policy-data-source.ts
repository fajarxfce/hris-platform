import type { ClientPolicyRevisionDto } from "../models/client-policy-revision-dto";
import type { ClientPolicySettingsDto } from "../models/client-policy-settings-dto";

export interface ClientPolicyDataSource {
  settings(companyId: string, signal: AbortSignal): Promise<ClientPolicySettingsDto>;
  revision(
    companyId: string,
    version: number,
    signal: AbortSignal,
  ): Promise<ClientPolicyRevisionDto>;
}
