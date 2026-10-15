import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpLifecycleCaseDataSource } from "../data/datasources/http-lifecycle-case-data-source";
import { HttpLifecycleTemplateDataSource } from "../data/datasources/http-lifecycle-template-data-source";
import { HttpOffboardingScheduleDataSource } from "../data/datasources/http-offboarding-schedule-data-source";
import { HttpOnboardingScheduleDataSource } from "../data/datasources/http-onboarding-schedule-data-source";
import { RemoteLifecycleCaseRepository } from "../data/repositories/remote-lifecycle-case-repository";
import { RemoteLifecycleTemplateRepository } from "../data/repositories/remote-lifecycle-template-repository";
import { RemoteOffboardingScheduleRepository } from "../data/repositories/remote-offboarding-schedule-repository";
import { RemoteOnboardingScheduleRepository } from "../data/repositories/remote-onboarding-schedule-repository";
import { AssignLifecycleTask } from "../domain/usecases/assign-lifecycle-task";
import { CancelLifecycleCase } from "../domain/usecases/cancel-lifecycle-case";
import { CancelOffboardingSchedule } from "../domain/usecases/cancel-offboarding-schedule";
import { CancelOnboardingSchedule } from "../domain/usecases/cancel-onboarding-schedule";
import { ChangeLifecycleTask } from "../domain/usecases/change-lifecycle-task";
import { CompleteOffboarding } from "../domain/usecases/complete-offboarding";
import { CompleteOnboarding } from "../domain/usecases/complete-onboarding";
import { LoadAssignedLifecycleTasks } from "../domain/usecases/load-assigned-lifecycle-tasks";
import { LoadLifecycleAssignees } from "../domain/usecases/load-lifecycle-assignees";
import { LoadLifecycleCase } from "../domain/usecases/load-lifecycle-case";
import { LoadLifecycleCases } from "../domain/usecases/load-lifecycle-cases";
import { LoadLifecycleHistory } from "../domain/usecases/load-lifecycle-history";
import { LoadLifecycleTemplate } from "../domain/usecases/load-lifecycle-template";
import { LoadLifecycleTemplates } from "../domain/usecases/load-lifecycle-templates";
import { LoadOffboardingReview } from "../domain/usecases/load-offboarding-review";
import { LoadOffboardingSchedule } from "../domain/usecases/load-offboarding-schedule";
import { LoadOffboardingScheduleReview } from "../domain/usecases/load-offboarding-schedule-review";
import { LoadOffboardingSchedules } from "../domain/usecases/load-offboarding-schedules";
import { LoadOnboardingSchedule } from "../domain/usecases/load-onboarding-schedule";
import { LoadOnboardingScheduleReview } from "../domain/usecases/load-onboarding-schedule-review";
import { LoadOnboardingSchedules } from "../domain/usecases/load-onboarding-schedules";
import { RecoverOffboardingSchedule } from "../domain/usecases/recover-offboarding-schedule";
import { RecoverOnboardingSchedule } from "../domain/usecases/recover-onboarding-schedule";
import { SaveLifecycleTemplate } from "../domain/usecases/save-lifecycle-template";
import { ScheduleOffboarding } from "../domain/usecases/schedule-offboarding";
import { ScheduleOnboarding } from "../domain/usecases/schedule-onboarding";
import { StartLifecycleCase } from "../domain/usecases/start-lifecycle-case";

export function createLifecycleFeature(http: HttpClient) {
  const templates = new RemoteLifecycleTemplateRepository(
    new HttpLifecycleTemplateDataSource(http),
  );
  const cases = new RemoteLifecycleCaseRepository(new HttpLifecycleCaseDataSource(http));
  const schedules = new RemoteOffboardingScheduleRepository(
    new HttpOffboardingScheduleDataSource(http),
  );
  const onboarding = new RemoteOnboardingScheduleRepository(
    new HttpOnboardingScheduleDataSource(http),
  );
  return {
    loadOnboardingScheduleReview: new LoadOnboardingScheduleReview(onboarding),
    loadOnboardingSchedules: new LoadOnboardingSchedules(onboarding),
    loadOnboardingSchedule: new LoadOnboardingSchedule(onboarding),
    scheduleOnboarding: new ScheduleOnboarding(onboarding),
    cancelOnboardingSchedule: new CancelOnboardingSchedule(onboarding),
    recoverOnboardingSchedule: new RecoverOnboardingSchedule(onboarding),
    loadOffboardingScheduleReview: new LoadOffboardingScheduleReview(schedules),
    loadOffboardingSchedules: new LoadOffboardingSchedules(schedules),
    loadOffboardingSchedule: new LoadOffboardingSchedule(schedules),
    scheduleOffboarding: new ScheduleOffboarding(schedules),
    cancelOffboardingSchedule: new CancelOffboardingSchedule(schedules),
    recoverOffboardingSchedule: new RecoverOffboardingSchedule(schedules),

    loadOffboardingReview: new LoadOffboardingReview(cases),
    completeOffboarding: new CompleteOffboarding(cases),
    cancelCase: new CancelLifecycleCase(cases),
    completeOnboarding: new CompleteOnboarding(cases),
    startCase: new StartLifecycleCase(cases),
    loadAssignees: new LoadLifecycleAssignees(cases),
    assignTask: new AssignLifecycleTask(cases),
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
