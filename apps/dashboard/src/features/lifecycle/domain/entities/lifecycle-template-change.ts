import type { LifecycleTaskDefinition } from "./lifecycle-task-definition";
import type { LifecycleKind } from "./lifecycle-template";

export type LifecycleTemplateChange = Readonly<{
  id: string;
  expectedVersion: number | null;
  code: string;
  name: string;
  kind: LifecycleKind;
  active: boolean;
  tasks: readonly LifecycleTaskDefinition[];
  reason: string;
}>;
