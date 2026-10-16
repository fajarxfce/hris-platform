import type { Failure } from "../../../../core/domain/result";
import type { ManagedAccountPage } from "../../domain/entities/managed-account-page";
export type ManagedAccountsState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: ManagedAccountPage | null;
  failure: Failure | null;
}>;
export const initialManagedAccountsState: ManagedAccountsState = Object.freeze({
  stage: "loading",
  page: null,
  failure: null,
});
