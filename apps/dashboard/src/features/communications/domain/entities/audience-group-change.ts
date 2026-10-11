export type AudienceGroupChange = Readonly<{
  id: string;
  expectedVersion: number | null;
  name: string;
  active: boolean;
  employmentIds: readonly string[];
  reason: string;
}>;
