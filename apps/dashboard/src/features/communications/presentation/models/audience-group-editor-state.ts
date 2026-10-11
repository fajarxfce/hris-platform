import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { AudienceGroup } from "../../domain/entities/audience-group";

export type AudienceGroupEditorState = Readonly<{
  stage: "loading" | "editing" | "saving" | "unconfirmed" | "conflict" | "saved" | "unavailable";
  group: AudienceGroup | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
  failure: Failure | null;
}>;
export const initialAudienceGroupEditorState: AudienceGroupEditorState = {
  stage: "loading",
  group: null,
  receipt: null,
  operationId: null,
  failure: null,
};
