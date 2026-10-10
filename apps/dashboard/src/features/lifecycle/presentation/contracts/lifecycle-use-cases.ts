import type { AssignLifecycleTask } from "../../domain/usecases/assign-lifecycle-task";
import type { ChangeLifecycleTask } from "../../domain/usecases/change-lifecycle-task";
import type { LoadAssignedLifecycleTasks } from "../../domain/usecases/load-assigned-lifecycle-tasks";
import type { LoadLifecycleAssignees } from "../../domain/usecases/load-lifecycle-assignees";
import type { LoadLifecycleCase } from "../../domain/usecases/load-lifecycle-case";
import type { LoadLifecycleCases } from "../../domain/usecases/load-lifecycle-cases";
import type { LoadLifecycleHistory } from "../../domain/usecases/load-lifecycle-history";
import type { LoadLifecycleTemplate } from "../../domain/usecases/load-lifecycle-template";
import type { LoadLifecycleTemplates } from "../../domain/usecases/load-lifecycle-templates";
import type { SaveLifecycleTemplate } from "../../domain/usecases/save-lifecycle-template";
import type { StartLifecycleCase } from "../../domain/usecases/start-lifecycle-case";

export type LifecycleUseCases = Readonly<{
  startCase: Pick<StartLifecycleCase, "execute">;
  loadAssignees: Pick<LoadLifecycleAssignees, "execute">;
  assignTask: Pick<AssignLifecycleTask, "execute">;
  changeTask: Pick<ChangeLifecycleTask, "execute">;
  loadCases: Pick<LoadLifecycleCases, "execute">;
  loadCase: Pick<LoadLifecycleCase, "execute">;
  loadHistory: Pick<LoadLifecycleHistory, "execute">;
  loadAssignedTasks: Pick<LoadAssignedLifecycleTasks, "execute">;
  loadTemplates: Pick<LoadLifecycleTemplates, "execute">;
  loadTemplate: Pick<LoadLifecycleTemplate, "execute">;
  saveTemplate: Pick<SaveLifecycleTemplate, "execute">;
}>;
