import { useEffect, useMemo, useSyncExternalStore } from "react";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { BackgroundJob } from "../../domain/entities/background-job";
import type { JobsUseCases } from "../contracts/jobs-use-cases";
import { JobDetailController } from "../controllers/job-detail-controller";
import { jobDetailView } from "../models/job-view";
import { JobDetailPanel } from "../pages/job-detail-panel";

export function JobDetailsBinding({
  id,
  access,
  jobs,
  locale,
  onClose,
  onObserved,
}: {
  id: string;
  access: CompanyAccess;
  jobs: JobsUseCases;
  locale: Locale;
  onClose: () => void;
  onObserved: (job: BackgroundJob) => void;
}) {
  const controller = useMemo(() => new JobDetailController(jobs, access, id), [jobs, access, id]);
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useEffect(() => {
    if (state.stage === "ready") onObserved(state.job);
  }, [state, onObserved]);
  const view = useMemo(
    () => (state.job ? jobDetailView(state.job, locale) : null),
    [state.job, locale],
  );
  return (
    <JobDetailPanel
      state={state}
      view={view}
      locale={locale}
      onClose={onClose}
      onRefresh={controller.refresh}
      onRequestCancellation={controller.requestConfirmation}
      onConfirmCancellation={controller.confirmCancellation}
      onDismissConfirmation={controller.dismissConfirmation}
    />
  );
}
