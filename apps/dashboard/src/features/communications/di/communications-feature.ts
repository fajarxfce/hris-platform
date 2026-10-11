import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpAnnouncementDataSource } from "../data/datasources/http-announcement-data-source";
import { RemoteAnnouncementRepository } from "../data/repositories/remote-announcement-repository";
import { LoadAnnouncement } from "../domain/usecases/load-announcement";
import { LoadAnnouncementHistory } from "../domain/usecases/load-announcement-history";
import { LoadAnnouncements } from "../domain/usecases/load-announcements";

export function createCommunicationsFeature(http: HttpClient) {
  const announcements = new RemoteAnnouncementRepository(new HttpAnnouncementDataSource(http));
  return {
    loadAnnouncements: new LoadAnnouncements(announcements),
    loadAnnouncement: new LoadAnnouncement(announcements),
    loadHistory: new LoadAnnouncementHistory(announcements),
  };
}
