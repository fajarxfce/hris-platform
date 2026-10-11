import type { LoadAnnouncement } from "../../domain/usecases/load-announcement";
import type { LoadAnnouncementHistory } from "../../domain/usecases/load-announcement-history";
import type { LoadAnnouncements } from "../../domain/usecases/load-announcements";

export type CommunicationsUseCases = Readonly<{
  loadAnnouncements: Pick<LoadAnnouncements, "execute">;
  loadAnnouncement: Pick<LoadAnnouncement, "execute">;
  loadHistory: Pick<LoadAnnouncementHistory, "execute">;
}>;
