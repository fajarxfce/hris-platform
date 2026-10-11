export type AnnouncementChangeDto = Readonly<{
  expectedVersion: number | null;
  title: string;
  body: string;
  audience: Readonly<{ kind: string; targetIds: readonly string[] }>;
  acknowledgementRequired: boolean;
  reason: string;
}>;
