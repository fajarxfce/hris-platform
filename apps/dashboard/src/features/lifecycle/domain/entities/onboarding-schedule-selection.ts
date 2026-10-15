import type { LifecycleTemplateId } from "./lifecycle-template";
export type OnboardingScheduleSelection = Readonly<{
  templateId: LifecycleTemplateId;
  targetDate: string;
  startsOn: string;
}>;
