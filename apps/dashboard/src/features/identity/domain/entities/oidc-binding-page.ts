import type { OidcBinding } from "./oidc-binding";

export type OidcBindingPage = Readonly<{
  items: readonly OidcBinding[];
  nextCursor: string | null;
}>;
