import type { LoadLifecycleTemplate } from "../../domain/usecases/load-lifecycle-template";
import type { LoadLifecycleTemplates } from "../../domain/usecases/load-lifecycle-templates";
import type { SaveLifecycleTemplate } from "../../domain/usecases/save-lifecycle-template";

export type LifecycleUseCases = Readonly<{
  loadTemplates: Pick<LoadLifecycleTemplates, "execute">;
  loadTemplate: Pick<LoadLifecycleTemplate, "execute">;
  saveTemplate: Pick<SaveLifecycleTemplate, "execute">;
}>;
