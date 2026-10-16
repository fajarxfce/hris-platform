import type { OidcBindingChangeDto } from "../models/oidc-binding-change-dto";
import type {
  OidcBindingDto,
  OidcBindingPageDto,
  OidcBindingReceiptDto,
} from "../models/oidc-binding-dto";

export interface OidcBindingDataSource {
  list(accountId: string, after: string | null, signal: AbortSignal): Promise<OidcBindingPageDto>;
  get(accountId: string, id: string, signal: AbortSignal): Promise<OidcBindingDto>;
  save(
    accountId: string,
    id: string,
    operation: string,
    input: OidcBindingChangeDto,
    signal: AbortSignal,
  ): Promise<OidcBindingReceiptDto>;
}
