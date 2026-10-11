import type { Failure } from "../../../../core/domain/result";
import type { Announcement } from "../../domain/entities/announcement";

export type AnnouncementState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  announcement: Announcement | null;
  failure: Failure | null;
}>;
export const initialAnnouncementState: AnnouncementState = Object.freeze({
  stage: "idle",
  announcement: null,
  failure: null,
});
