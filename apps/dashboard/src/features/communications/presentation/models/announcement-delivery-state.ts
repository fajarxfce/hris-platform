import type { Failure } from "../../../../core/domain/result";
import type { AnnouncementDelivery } from "../../domain/entities/announcement-delivery";
export type AnnouncementDeliveryState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  delivery: AnnouncementDelivery | null;
  failure: Failure | null;
}>;
export const initialAnnouncementDeliveryState: AnnouncementDeliveryState = {
  stage: "idle",
  delivery: null,
  failure: null,
};
