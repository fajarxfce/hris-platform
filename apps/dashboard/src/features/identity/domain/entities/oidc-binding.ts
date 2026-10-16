import type { AccountId } from "../../../../core/domain/identifiers";

export type OidcBindingId = string & { readonly oidcBindingId: unique symbol };
export type OidcBinding = Readonly<{
  id: OidcBindingId;
  accountId: AccountId;
  issuer: string;
  subject: string;
  active: boolean;
  version: number;
  linkedAt: string;
}>;
