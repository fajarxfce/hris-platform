import type { LoadAnnouncement } from "../../domain/usecases/load-announcement";
import type { LoadAnnouncementHistory } from "../../domain/usecases/load-announcement-history";
import type { LoadAnnouncements } from "../../domain/usecases/load-announcements";
import type { LoadAudienceGroup } from "../../domain/usecases/load-audience-group";
import type { LoadAudienceGroups } from "../../domain/usecases/load-audience-groups";
import type { LoadAudienceReferences } from "../../domain/usecases/load-audience-references";
import type { SaveAnnouncement } from "../../domain/usecases/save-announcement";
import type { SaveAudienceGroup } from "../../domain/usecases/save-audience-group";

export type CommunicationsUseCases = Readonly<{
  loadGroup: Pick<LoadAudienceGroup, "execute">;
  loadGroups: Pick<LoadAudienceGroups, "execute">;
  saveGroup: Pick<SaveAudienceGroup, "execute">;
  saveAnnouncement: Pick<SaveAnnouncement, "execute">;
  loadReferences: Pick<LoadAudienceReferences, "execute">;
  loadAnnouncements: Pick<LoadAnnouncements, "execute">;
  loadAnnouncement: Pick<LoadAnnouncement, "execute">;
  loadHistory: Pick<LoadAnnouncementHistory, "execute">;
}>;
