import type { AnnouncementChangeDto } from "../models/announcement-change-dto";
import type { AnnouncementDto, AnnouncementPageDto } from "../models/announcement-dto";
import type { CommunicationsReceiptDto } from "../models/communications-receipt-dto";

export interface AnnouncementDataSource {
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
