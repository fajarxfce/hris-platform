import type { LifecycleTemplate } from "./lifecycle-template";

export type LifecycleTemplatePage = Readonly<{
  items: readonly LifecycleTemplate[];
  nextCursor: string | null;
}>;
