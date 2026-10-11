import type { LoadAnnouncement } from "../../domain/usecases/load-announcement";
import type { LoadAnnouncementHistory } from "../../domain/usecases/load-announcement-history";
import type { LoadAnnouncements } from "../../domain/usecases/load-announcements";
import type { LoadAudienceReferences } from "../../domain/usecases/load-audience-references";
import type { SaveAnnouncement } from "../../domain/usecases/save-announcement";

export type CommunicationsUseCases = Readonly<{
  saveAnnouncement: Pick<SaveAnnouncement, "execute">;
  loadReferences: Pick<LoadAudienceReferences, "execute">;
  loadAnnouncements: Pick<LoadAnnouncements, "execute">;
  loadAnnouncement: Pick<LoadAnnouncement, "execute">;
  loadHistory: Pick<LoadAnnouncementHistory, "execute">;
}>;
