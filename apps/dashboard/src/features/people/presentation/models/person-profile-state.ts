import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { PersonProfile } from "../../domain/entities/person-profile";

export type PersonProfileState = Readonly<{
  stage:
    | "loading"
    | "ready"
    | "editing"
    | "saving"
    | "saved"
    | "unconfirmed"
    | "conflict"
    | "unavailable";
  profile: PersonProfile | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: OperationId | null;
}>;
export const initialPersonProfileState: PersonProfileState = {
  stage: "loading",
  profile: null,
  failure: null,
  receipt: null,
  operationId: null,
};
