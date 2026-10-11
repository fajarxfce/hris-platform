import type { AudienceKind } from "./announcement";

export type AnnouncementChange = Readonly<{
  id: string;
  expectedVersion: number | null;
  title: string;
  body: string;
  audienceKind: AudienceKind;
  targetIds: readonly string[];
  acknowledgementRequired: boolean;
  reason: string;
}>;
