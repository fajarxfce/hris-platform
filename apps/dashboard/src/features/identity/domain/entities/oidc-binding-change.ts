import type { AccountId } from "../../../../core/domain/identifiers";
import type { OidcBindingId } from "./oidc-binding";

export type OidcBindingChange = Readonly<{
  accountId: AccountId;
  id: OidcBindingId;
  issuer: string;
  subject: string;
  active: boolean;
  expectedVersion: number | null;
  reason: string;
}>;
