import type { Failure } from "../../../../core/domain/result";
import type { CompanyMemberGrant } from "../../domain/entities/company-member";

export type CompanyMemberState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  grant: CompanyMemberGrant | null;
  failure: Failure | null;
}>;
export const initialCompanyMemberState: CompanyMemberState = Object.freeze({
  stage: "idle",
  grant: null,
  failure: null,
});
