import type { AnnouncementDto, AnnouncementPageDto } from "../models/announcement-dto";

export interface AnnouncementDataSource {
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
