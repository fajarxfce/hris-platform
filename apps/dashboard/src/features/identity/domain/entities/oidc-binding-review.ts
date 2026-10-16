import type { ManagedAccount } from "./managed-account";
import type { OidcBinding } from "./oidc-binding";

export type OidcBindingReview = Readonly<{
  account: ManagedAccount;
  binding: OidcBinding | null;
}>;
