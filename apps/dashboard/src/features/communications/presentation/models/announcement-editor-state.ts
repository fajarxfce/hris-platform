import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { Announcement } from "../../domain/entities/announcement";

export type AnnouncementEditorState = Readonly<{
  stage: "loading" | "editing" | "saving" | "unconfirmed" | "conflict" | "saved" | "unavailable";
  announcement: Announcement | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialAnnouncementEditorState: AnnouncementEditorState = Object.freeze({
  stage: "loading",
  announcement: null,
  failure: null,
  receipt: null,
  operationId: null,
});
