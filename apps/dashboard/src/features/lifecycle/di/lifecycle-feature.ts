import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpLifecycleCaseDataSource } from "../data/datasources/http-lifecycle-case-data-source";
import { HttpLifecycleTemplateDataSource } from "../data/datasources/http-lifecycle-template-data-source";
import { RemoteLifecycleCaseRepository } from "../data/repositories/remote-lifecycle-case-repository";
import { RemoteLifecycleTemplateRepository } from "../data/repositories/remote-lifecycle-template-repository";
import { ChangeLifecycleTask } from "../domain/usecases/change-lifecycle-task";
import { LoadAssignedLifecycleTasks } from "../domain/usecases/load-assigned-lifecycle-tasks";
import { LoadLifecycleCase } from "../domain/usecases/load-lifecycle-case";
import { LoadLifecycleCases } from "../domain/usecases/load-lifecycle-cases";
import { LoadLifecycleHistory } from "../domain/usecases/load-lifecycle-history";
import { LoadLifecycleTemplate } from "../domain/usecases/load-lifecycle-template";
import { LoadLifecycleTemplates } from "../domain/usecases/load-lifecycle-templates";
import { SaveLifecycleTemplate } from "../domain/usecases/save-lifecycle-template";

export function createLifecycleFeature(http: HttpClient) {
  const templates = new RemoteLifecycleTemplateRepository(
    new HttpLifecycleTemplateDataSource(http),
  );
  const cases = new RemoteLifecycleCaseRepository(new HttpLifecycleCaseDataSource(http));
  return {
    changeTask: new ChangeLifecycleTask(cases),
    loadCases: new LoadLifecycleCases(cases),
    loadCase: new LoadLifecycleCase(cases),
    loadHistory: new LoadLifecycleHistory(cases),
    loadAssignedTasks: new LoadAssignedLifecycleTasks(cases),
    loadTemplates: new LoadLifecycleTemplates(templates),
    loadTemplate: new LoadLifecycleTemplate(templates),
    saveTemplate: new SaveLifecycleTemplate(templates),
  };
}
