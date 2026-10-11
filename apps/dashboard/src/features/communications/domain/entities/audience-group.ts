export type AudienceGroupId = string & { readonly __brand: "AudienceGroupId" };
export type AudienceGroupSummary = Readonly<{
  id: AudienceGroupId;
  version: number;
  name: string;
  active: boolean;
  memberCount: number;
  recordedAt: string;
}>;
export type AudienceGroup = AudienceGroupSummary &
  Readonly<{
    employmentIds: readonly string[];
    recordedBy: string;
    reason: string;
  }>;
export type AudienceGroupPage = Readonly<{
  items: readonly AudienceGroupSummary[];
  nextCursor: string | null;
}>;
