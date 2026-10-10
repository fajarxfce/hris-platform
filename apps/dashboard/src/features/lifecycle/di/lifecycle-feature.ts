import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpLifecycleTemplateDataSource } from "../data/datasources/http-lifecycle-template-data-source";
import { RemoteLifecycleTemplateRepository } from "../data/repositories/remote-lifecycle-template-repository";
import { LoadLifecycleTemplate } from "../domain/usecases/load-lifecycle-template";
import { LoadLifecycleTemplates } from "../domain/usecases/load-lifecycle-templates";
import { SaveLifecycleTemplate } from "../domain/usecases/save-lifecycle-template";

export function createLifecycleFeature(http: HttpClient) {
  const templates = new RemoteLifecycleTemplateRepository(
    new HttpLifecycleTemplateDataSource(http),
  );
  return {
    loadTemplates: new LoadLifecycleTemplates(templates),
    loadTemplate: new LoadLifecycleTemplate(templates),
    saveTemplate: new SaveLifecycleTemplate(templates),
  };
}
