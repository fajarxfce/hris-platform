export type LifecycleTaskDefinition = Readonly<{
  key: string;
  title: string;
  required: boolean;
  dueDays: number;
}>;
