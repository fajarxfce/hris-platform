import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { ClientPolicySettings } from "../../domain/entities/client-policy-settings";

export type ClientPolicyEditorState = Readonly<{
  stage: "loading" | "editing" | "saving" | "unconfirmed" | "conflict" | "saved" | "unavailable";
  settings: ClientPolicySettings | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialClientPolicyEditorState: ClientPolicyEditorState = {
  stage: "loading",
  settings: null,
  failure: null,
  receipt: null,
  operationId: null,
};
