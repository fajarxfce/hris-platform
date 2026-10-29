import type { HttpClient } from "../../../../core/data/http/http-client";
import { announcementDeliveryDto } from "../models/announcement-delivery-dto";
import type { AnnouncementDeliveryDataSource } from "./announcement-delivery-data-source";
export class HttpAnnouncementDeliveryDataSource implements AnnouncementDeliveryDataSource {
  constructor(private readonly http: HttpClient) {}
  async get(company: string, id: string, signal: AbortSignal) {
    return announcementDeliveryDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/announcements/${id}/delivery` },
        signal,
      ),
    );
  }
}
