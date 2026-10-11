export type AudienceGroupChangeDto = {
  expectedVersion: number | null;
  name: string;
  active: boolean;
  employmentIds: string[];
  reason: string;
};
