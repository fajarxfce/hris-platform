import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { BackgroundJob } from "../../domain/entities/background-job";
import type { JobSearch } from "../../domain/entities/job-search";
import type { JobsUseCases } from "../contracts/jobs-use-cases";
import { initialJobListState, type JobListState } from "../models/job-list-state";

export class JobListController {
  #state: JobListState = initialJobListState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  private readonly search: JobSearch;
  constructor(
    private readonly load: JobsUseCases["loadJobs"],
    private readonly access: CompanyAccess,
    search: JobSearch,
  ) {
    this.search = Object.freeze({ ...search });
  }
  getSnapshot = (): JobListState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    if (this.#active) return;
    this.#active = true;
    void this.refresh();
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    this.publish(initialJobListState);
  };
  observeJob = (job: BackgroundJob): void => {
    const page = this.#state.page;
    if (!this.#active || !page || job.companyId !== this.access.companyId) return;
    const previous = page.items.find((item) => item.id === job.id);
    if (!previous || previous.version > job.version || previous === job) return;
    this.publish({
      ...this.#state,
      page: Object.freeze({
        ...page,
        items: Object.freeze(page.items.map((item) => (item.id === job.id ? job : item))),
      }),
    });
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "loading", page: null, failure: null });
    try {
      const result = await this.load.execute(this.access, this.search, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", page: result.value, failure: null }
          : { stage: "unavailable", page: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          page: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: JobListState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
