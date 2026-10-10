import type { Failure } from "../../../../core/domain/result";
import type { CompanyMemberPage } from "../../domain/entities/company-member";

export type CompanyMembersState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  page: CompanyMemberPage | null;
  failure: Failure | null;
}>;
export const initialCompanyMembersState: CompanyMembersState = Object.freeze({
  stage: "idle",
  page: null,
  failure: null,
});
