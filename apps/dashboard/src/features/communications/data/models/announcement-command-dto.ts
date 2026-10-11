export type AnnouncementCommandPath = "publish" | "return-to-draft" | "archive";
export type AnnouncementCommandDto = {
  expectedVersion: number;
  reason: string;
  scheduledFor?: string | null;
};
