import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { AnnouncementAudiencePreview } from "../../domain/entities/announcement-audience-preview";
import type { AnnouncementReview } from "../../domain/entities/announcement-review";

export type AnnouncementCommandState = Readonly<{
  stage:
    | "loading"
    | "editing"
    | "confirming"
    | "saving"
    | "unconfirmed"
    | "conflict"
    | "saved"
    | "unavailable";
  review: AnnouncementReview | null;
  preview: AnnouncementAudiencePreview | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
  failure: Failure | null;
}>;
export const initialAnnouncementCommandState: AnnouncementCommandState = {
  stage: "loading",
  review: null,
  preview: null,
  receipt: null,
  operationId: null,
  failure: null,
};
