import type { LoadAssignedLifecycleTasks } from "../../domain/usecases/load-assigned-lifecycle-tasks";
import type { LoadLifecycleCase } from "../../domain/usecases/load-lifecycle-case";
import type { LoadLifecycleCases } from "../../domain/usecases/load-lifecycle-cases";
import type { LoadLifecycleHistory } from "../../domain/usecases/load-lifecycle-history";
import type { LoadLifecycleTemplate } from "../../domain/usecases/load-lifecycle-template";
import type { LoadLifecycleTemplates } from "../../domain/usecases/load-lifecycle-templates";
import type { SaveLifecycleTemplate } from "../../domain/usecases/save-lifecycle-template";

export type LifecycleUseCases = Readonly<{
  loadCases: Pick<LoadLifecycleCases, "execute">;
  loadCase: Pick<LoadLifecycleCase, "execute">;
  loadHistory: Pick<LoadLifecycleHistory, "execute">;
  loadAssignedTasks: Pick<LoadAssignedLifecycleTasks, "execute">;
  loadTemplates: Pick<LoadLifecycleTemplates, "execute">;
  loadTemplate: Pick<LoadLifecycleTemplate, "execute">;
  saveTemplate: Pick<SaveLifecycleTemplate, "execute">;
}>;
