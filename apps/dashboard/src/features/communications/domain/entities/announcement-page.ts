import type { AnnouncementSummary } from "./announcement";

export type AnnouncementPage = Readonly<{
  items: readonly AnnouncementSummary[];
  nextCursor: string | null;
}>;
