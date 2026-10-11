import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { Announcement, AnnouncementId } from "../entities/announcement";
import type { AnnouncementPage } from "../entities/announcement-page";

export interface AnnouncementRepository {
  list(
    company: CompanyId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<AnnouncementPage>>;
  get(
    company: CompanyId,
    id: AnnouncementId,
    version: number | null,
    signal: AbortSignal,
  ): Promise<Result<Announcement>>;
  history(
    company: CompanyId,
    id: AnnouncementId,
    after: number | null,
    signal: AbortSignal,
  ): Promise<Result<AnnouncementPage>>;
}
