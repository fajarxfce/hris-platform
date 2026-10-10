export type LifecycleCaseStart = Readonly<{
  id: string;
  employmentId: string;
  templateId: string;
  templateVersion: number;
  targetDate: string;
  assignees: Readonly<Record<string, string>>;
  reason: string;
}>;
