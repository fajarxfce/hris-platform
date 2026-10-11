import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpAnnouncementDataSource } from "../data/datasources/http-announcement-data-source";
import { HttpAudienceReferenceDataSource } from "../data/datasources/http-audience-reference-data-source";
import { RemoteAnnouncementRepository } from "../data/repositories/remote-announcement-repository";
import { RemoteAudienceReferenceRepository } from "../data/repositories/remote-audience-reference-repository";
import { LoadAnnouncement } from "../domain/usecases/load-announcement";
import { LoadAnnouncementHistory } from "../domain/usecases/load-announcement-history";
import { LoadAnnouncements } from "../domain/usecases/load-announcements";
import { LoadAudienceReferences } from "../domain/usecases/load-audience-references";
import { SaveAnnouncement } from "../domain/usecases/save-announcement";

export function createCommunicationsFeature(http: HttpClient) {
  const announcements = new RemoteAnnouncementRepository(new HttpAnnouncementDataSource(http));
  const references = new RemoteAudienceReferenceRepository(
    new HttpAudienceReferenceDataSource(http),
  );
  return {
    saveAnnouncement: new SaveAnnouncement(announcements),
    loadReferences: new LoadAudienceReferences(references),
    loadAnnouncements: new LoadAnnouncements(announcements),
    loadAnnouncement: new LoadAnnouncement(announcements),
    loadHistory: new LoadAnnouncementHistory(announcements),
  };
}
