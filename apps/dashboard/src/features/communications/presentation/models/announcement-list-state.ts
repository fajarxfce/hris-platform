import type { Failure } from "../../../../core/domain/result";
import type { AnnouncementPage } from "../../domain/entities/announcement-page";

export type AnnouncementListState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  page: AnnouncementPage | null;
  failure: Failure | null;
}>;
export const initialAnnouncementListState: AnnouncementListState = Object.freeze({
  stage: "idle",
  page: null,
  failure: null,
});
