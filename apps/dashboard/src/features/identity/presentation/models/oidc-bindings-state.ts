import type { Failure } from "../../../../core/domain/result";
import type { OidcBindingDirectory } from "../../domain/entities/oidc-binding-directory";

export type OidcBindingsState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  directory: OidcBindingDirectory | null;
  failure: Failure | null;
}>;
export const initialOidcBindingsState: OidcBindingsState = Object.freeze({
  stage: "loading",
  directory: null,
  failure: null,
});
