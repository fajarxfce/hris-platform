import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { CompanyMember } from "../../../identity/domain/entities/company-member";
import type { PersonProfile } from "../../domain/entities/person-profile";

export type PersonAccountBindingState = Readonly<{
  stage:
    | "idle"
    | "loading"
    | "editing"
    | "submitting"
    | "unconfirmed"
    | "bound"
    | "conflict"
    | "blocked"
    | "unavailable";
  profile: PersonProfile | null;
  candidates: readonly CompanyMember[];
  candidateAfter: string | null;
  candidateNext: string | null;
  loadingCandidates: boolean;
  selected: CompanyMember | null;
  failure: Failure | null;
  operationId: string | null;
  receipt: MutationReceipt | null;
}>;
export const initialPersonAccountBindingState: PersonAccountBindingState = Object.freeze({
  stage: "idle",
  profile: null,
  candidates: Object.freeze([]),
  candidateAfter: null,
  candidateNext: null,
  loadingCandidates: false,
  selected: null,
  failure: null,
  operationId: null,
  receipt: null,
});
