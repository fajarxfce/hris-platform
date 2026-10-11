import type { ArchiveAnnouncement } from "../../domain/usecases/archive-announcement";
import type { LoadAnnouncement } from "../../domain/usecases/load-announcement";
import type { LoadAnnouncementHistory } from "../../domain/usecases/load-announcement-history";
import type { LoadAnnouncementReview } from "../../domain/usecases/load-announcement-review";
import type { LoadAnnouncements } from "../../domain/usecases/load-announcements";
import type { LoadAudienceGroup } from "../../domain/usecases/load-audience-group";
import type { LoadAudienceGroups } from "../../domain/usecases/load-audience-groups";
import type { LoadAudienceReferences } from "../../domain/usecases/load-audience-references";
import type { PreviewAnnouncementAudience } from "../../domain/usecases/preview-announcement-audience";
import type { PublishAnnouncement } from "../../domain/usecases/publish-announcement";
import type { ReturnAnnouncementToDraft } from "../../domain/usecases/return-announcement-to-draft";
import type { SaveAnnouncement } from "../../domain/usecases/save-announcement";
import type { SaveAudienceGroup } from "../../domain/usecases/save-audience-group";

export type CommunicationsUseCases = Readonly<{
  loadReview: Pick<LoadAnnouncementReview, "execute">;
  previewAudience: Pick<PreviewAnnouncementAudience, "execute">;
  publish: Pick<PublishAnnouncement, "execute">;
  archive: Pick<ArchiveAnnouncement, "execute">;
  returnToDraft: Pick<ReturnAnnouncementToDraft, "execute">;
  loadGroup: Pick<LoadAudienceGroup, "execute">;
  loadGroups: Pick<LoadAudienceGroups, "execute">;
  saveGroup: Pick<SaveAudienceGroup, "execute">;
  saveAnnouncement: Pick<SaveAnnouncement, "execute">;
  loadReferences: Pick<LoadAudienceReferences, "execute">;
  loadAnnouncements: Pick<LoadAnnouncements, "execute">;
  loadAnnouncement: Pick<LoadAnnouncement, "execute">;
  loadHistory: Pick<LoadAnnouncementHistory, "execute">;
}>;
