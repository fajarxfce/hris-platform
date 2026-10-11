import type { AnnouncementAudiencePreviewDto } from "../models/announcement-audience-preview-dto";
import type { AnnouncementChangeDto } from "../models/announcement-change-dto";
import type {
  AnnouncementCommandDto,
  AnnouncementCommandPath,
} from "../models/announcement-command-dto";
import type { AnnouncementDto, AnnouncementPageDto } from "../models/announcement-dto";
import type { AnnouncementReviewDto } from "../models/announcement-review-dto";
import type { CommunicationsReceiptDto } from "../models/communications-receipt-dto";

export interface AnnouncementDataSource {
  review(company: string, id: string, signal: AbortSignal): Promise<AnnouncementReviewDto>;
  preview(
    company: string,
    id: string,
    version: number,
    signal: AbortSignal,
  ): Promise<AnnouncementAudiencePreviewDto>;
  command(
    company: string,
    id: string,
    operation: string,
    action: AnnouncementCommandPath,
    input: AnnouncementCommandDto,
    signal: AbortSignal,
  ): Promise<CommunicationsReceiptDto>;
  save(
    company: string,
    id: string,
    operation: string,
    change: AnnouncementChangeDto,
    signal: AbortSignal,
  ): Promise<CommunicationsReceiptDto>;
  list(company: string, after: string | null, signal: AbortSignal): Promise<AnnouncementPageDto>;
  get(
    company: string,
    id: string,
    version: number | null,
    signal: AbortSignal,
  ): Promise<AnnouncementDto>;
  history(
    company: string,
    id: string,
    after: number | null,
    signal: AbortSignal,
  ): Promise<AnnouncementPageDto>;
}
