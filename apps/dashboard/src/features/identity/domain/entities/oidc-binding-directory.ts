import type { ManagedAccount } from "./managed-account";
import type { OidcBindingPage } from "./oidc-binding-page";

export type OidcBindingDirectory = Readonly<{
  account: ManagedAccount;
  page: OidcBindingPage;
}>;
