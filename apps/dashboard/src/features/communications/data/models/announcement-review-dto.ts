import { z } from "zod";
import { announcementDto } from "./announcement-dto";

export const announcementReviewDto = z.object({
  announcement: announcementDto,
  publicationJob: z
    .object({
      id: z.uuid(),
      status: z.enum(["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED"]),
      cancellationRequested: z.boolean(),
      version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
      failureCode: z.string().min(1).max(160).nullable(),
    })
    .nullable(),
  availableActions: z
    .array(z.enum(["EDIT", "PREVIEW", "PUBLISH", "RETURN_TO_DRAFT", "ARCHIVE", "VIEW_JOB"]))
    .max(6),
  evaluatedAt: z.iso.datetime({ offset: true }),
});
export type AnnouncementReviewDto = z.infer<typeof announcementReviewDto>;
