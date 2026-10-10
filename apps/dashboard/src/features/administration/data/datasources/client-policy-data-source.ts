import type {
  ClientPolicyChangeDto,
  ClientPolicyReceiptDto,
} from "../models/client-policy-change-dto";
import type { ClientPolicyRevisionDto } from "../models/client-policy-revision-dto";
import type { ClientPolicySettingsDto } from "../models/client-policy-settings-dto";

export interface ClientPolicyDataSource {
  save(
    companyId: string,
    operation: string,
    input: ClientPolicyChangeDto,
    signal: AbortSignal,
  ): Promise<ClientPolicyReceiptDto>;
  settings(companyId: string, signal: AbortSignal): Promise<ClientPolicySettingsDto>;
  revision(
    companyId: string,
    version: number,
    signal: AbortSignal,
  ): Promise<ClientPolicyRevisionDto>;
}
