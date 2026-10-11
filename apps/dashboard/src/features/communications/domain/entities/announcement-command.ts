export type AnnouncementCommandInput = Readonly<{
  id: string;
  expectedVersion: number;
  reason: string;
}>;
export type AnnouncementCommand =
  | (AnnouncementCommandInput & Readonly<{ action: "PUBLISH"; scheduledFor: string | null }>)
  | (AnnouncementCommandInput & Readonly<{ action: "RETURN_TO_DRAFT" }>)
  | (AnnouncementCommandInput & Readonly<{ action: "ARCHIVE" }>);
export type AnnouncementCommandKind = AnnouncementCommand["action"];
