import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { firstJobPage, type JobSearch } from "../../domain/entities/job-search";
import type { JobsUseCases } from "../contracts/jobs-use-cases";
import { JobListController } from "../controllers/job-list-controller";
import { jobSearchParameters } from "../models/job-route";
import { jobListView } from "../models/job-view";
import { JobsPage } from "../pages/jobs-page";
import { JobDetailsBinding } from "./job-details-binding";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  jobs: JobsUseCases;
  companyName: string;
  locale: Locale;
};

export function JobsScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const ownsParameters =
    !parameters.has("company") || parameters.get("company") === props.access.companyId;
  const beforeAt = ownsParameters ? parameters.get("beforeAt") : null;
  const beforeId = ownsParameters ? parameters.get("beforeId") : null;
  const selected = ownsParameters ? parameters.get("job") : null;
  const search = useMemo(() => Object.freeze({ beforeAt, beforeId }), [beforeAt, beforeId]);
  return (
    <JobsBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      search={search}
      selected={selected}
      onSearch={(search, selected) =>
        setParameters(jobSearchParameters(search, props.access.companyId, selected))
      }
    />
  );
}

function JobsBinding({
  accountId,
  access,
  jobs,
  companyName,
  locale,
  search,
  selected,
  onSearch,
}: Props & {
  search: JobSearch;
  selected: string | null;
  onSearch: (search: JobSearch, selected: string | null) => void;
}) {
  const controller = useMemo(
    () => new JobListController(jobs.loadJobs, access, search),
    [jobs.loadJobs, access, search],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  const rows = useMemo(
    () => (state.page ? jobListView(state.page, locale) : []),
    [state.page, locale],
  );
  return (
    <>
      <JobsPage
        state={state}
        rows={rows}
        companyName={companyName}
        companyWide={access.permissions.includes("jobs.read")}
        locale={locale}
        firstPage={search.beforeAt === null && search.beforeId === null}
        onRefresh={controller.refresh}
        onFirstPage={() => onSearch(firstJobPage, null)}
        onOlderPage={() => {
          if (state.page?.next) onSearch(state.page.next, null);
        }}
        onOpenJob={(id) => onSearch(search, id)}
      />
      {selected !== null && (
        <JobDetailsBinding
          key={`${accountId}:${access.companyId}:${selected}`}
          id={selected}
          access={access}
          jobs={jobs}
          locale={locale}
          onObserved={controller.observeJob}
          onClose={() => onSearch(search, null)}
        />
      )}
    </>
  );
}
