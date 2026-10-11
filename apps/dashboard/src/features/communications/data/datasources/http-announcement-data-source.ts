import type { HttpClient } from "../../../../core/data/http/http-client";
import { announcementAudiencePreviewDto } from "../models/announcement-audience-preview-dto";
import type { AnnouncementChangeDto } from "../models/announcement-change-dto";
import type {
  AnnouncementCommandDto,
  AnnouncementCommandPath,
} from "../models/announcement-command-dto";
import { announcementDto, announcementPageDto } from "../models/announcement-dto";
import { announcementReviewDto } from "../models/announcement-review-dto";
import { communicationsReceiptDto } from "../models/communications-receipt-dto";
import type { AnnouncementDataSource } from "./announcement-data-source";

export class HttpAnnouncementDataSource implements AnnouncementDataSource {
  constructor(private readonly http: HttpClient) {}
  async review(company: string, id: string, signal: AbortSignal) {
    return announcementReviewDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/announcements/${id}/publication-review`,
        },
        signal,
      ),
    );
  }
  async preview(company: string, id: string, version: number, signal: AbortSignal) {
    return announcementAudiencePreviewDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/announcements/${id}/audience-preview?expectedVersion=${version}`,
        },
        signal,
      ),
    );
  }
  async command(
    company: string,
    id: string,
    operation: string,
    action: AnnouncementCommandPath,
    input: AnnouncementCommandDto,
    signal: AbortSignal,
  ) {
    return communicationsReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/announcements/${id}/${action}`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
  async save(
    company: string,
    id: string,
    operation: string,
    change: AnnouncementChangeDto,
    signal: AbortSignal,
  ) {
    return communicationsReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/announcements/${id}`,
          method: "PUT",
          operationId: operation,
          body: change,
        },
        signal,
      ),
    );
  }
  async list(company: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "50" });
    if (after !== null) query.set("after", after);
    return announcementPageDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/announcements?${query}`,
        },
        signal,
      ),
    );
  }
  async get(company: string, id: string, version: number | null, signal: AbortSignal) {
    return announcementDto.parse(
      await this.http.request(
        {
          path:
            "/api/v1/companies/" +
            company +
            "/announcements/" +
            id +
            (version === null ? "" : `/revisions/${version}`),
        },
        signal,
      ),
    );
  }
  async history(company: string, id: string, after: number | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "50" });
    if (after !== null) query.set("after", String(after));
    return announcementPageDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/announcements/${id}/history?${query}`,
        },
        signal,
      ),
    );
  }
}
