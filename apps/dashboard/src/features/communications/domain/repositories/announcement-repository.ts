import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { Announcement, AnnouncementId } from "../entities/announcement";
import type { AnnouncementAudiencePreview } from "../entities/announcement-audience-preview";
import type { AnnouncementChange } from "../entities/announcement-change";
import type { AnnouncementCommand } from "../entities/announcement-command";
import type { AnnouncementPage } from "../entities/announcement-page";
import type { AnnouncementReview } from "../entities/announcement-review";

export interface AnnouncementRepository {
  review(
    company: CompanyId,
    id: AnnouncementId,
    signal: AbortSignal,
  ): Promise<Result<AnnouncementReview>>;
  preview(
    company: CompanyId,
    announcement: Announcement,
    signal: AbortSignal,
  ): Promise<Result<AnnouncementAudiencePreview>>;
  command(
    company: CompanyId,
    operation: OperationId,
    command: AnnouncementCommand,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  save(
    company: CompanyId,
    operation: OperationId,
    change: AnnouncementChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
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
