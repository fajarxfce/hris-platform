import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpAnnouncementDataSource } from "../data/datasources/http-announcement-data-source";
import { HttpAudienceGroupDataSource } from "../data/datasources/http-audience-group-data-source";
import { HttpAudienceReferenceDataSource } from "../data/datasources/http-audience-reference-data-source";
import { RemoteAnnouncementRepository } from "../data/repositories/remote-announcement-repository";
import { RemoteAudienceGroupRepository } from "../data/repositories/remote-audience-group-repository";
import { RemoteAudienceReferenceRepository } from "../data/repositories/remote-audience-reference-repository";
import { ArchiveAnnouncement } from "../domain/usecases/archive-announcement";
import { LoadAnnouncement } from "../domain/usecases/load-announcement";
import { LoadAnnouncementHistory } from "../domain/usecases/load-announcement-history";
import { LoadAnnouncementReview } from "../domain/usecases/load-announcement-review";
import { LoadAnnouncements } from "../domain/usecases/load-announcements";
import { LoadAudienceGroup } from "../domain/usecases/load-audience-group";
import { LoadAudienceGroups } from "../domain/usecases/load-audience-groups";
import { LoadAudienceReferences } from "../domain/usecases/load-audience-references";
import { PreviewAnnouncementAudience } from "../domain/usecases/preview-announcement-audience";
import { PublishAnnouncement } from "../domain/usecases/publish-announcement";
import { ReturnAnnouncementToDraft } from "../domain/usecases/return-announcement-to-draft";
import { SaveAnnouncement } from "../domain/usecases/save-announcement";
import { SaveAudienceGroup } from "../domain/usecases/save-audience-group";

export function createCommunicationsFeature(http: HttpClient) {
  const groups = new RemoteAudienceGroupRepository(new HttpAudienceGroupDataSource(http));
  const announcements = new RemoteAnnouncementRepository(new HttpAnnouncementDataSource(http));
  const references = new RemoteAudienceReferenceRepository(
    new HttpAudienceReferenceDataSource(http),
  );
  return {
    loadReview: new LoadAnnouncementReview(announcements),
    previewAudience: new PreviewAnnouncementAudience(announcements),
    publish: new PublishAnnouncement(announcements),
    archive: new ArchiveAnnouncement(announcements),
    returnToDraft: new ReturnAnnouncementToDraft(announcements),
    loadGroup: new LoadAudienceGroup(groups),
    loadGroups: new LoadAudienceGroups(groups),
    saveGroup: new SaveAudienceGroup(groups),
    saveAnnouncement: new SaveAnnouncement(announcements),
    loadReferences: new LoadAudienceReferences(references),
    loadAnnouncements: new LoadAnnouncements(announcements),
    loadAnnouncement: new LoadAnnouncement(announcements),
    loadHistory: new LoadAnnouncementHistory(announcements),
  };
}
