import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canRequestJobCancellation } from "../../domain/policies/job-policy";
import type { JobsUseCases } from "../contracts/jobs-use-cases";
import { initialJobDetailState, type JobDetailState } from "../models/job-detail-state";

/** Owns one job panel and at most one read or cancellation. A failed command needs a fresh status read. */
export class JobDetailController {
  #state: JobDetailState = initialJobDetailState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<JobsUseCases, "loadJob" | "requestCancellation">,
    private readonly access: CompanyAccess,
    private readonly id: string,
  ) {}
  getSnapshot = (): JobDetailState => this.#state;
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
    this.publish(initialJobDetailState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#state.stage === "cancelling") return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "loading", job: null, failure: null });
    try {
      const result = await this.actions.loadJob.execute(this.access, this.id, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", job: result.value, failure: null }
          : { stage: "unavailable", job: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          job: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  requestConfirmation = (): void => {
    if (this.#active && this.#state.stage === "ready" && canRequestJobCancellation(this.#state.job))
      this.publish({ ...this.#state, stage: "confirming" });
  };
  dismissConfirmation = (): void => {
    if (this.#state.stage === "confirming") this.publish({ ...this.#state, stage: "ready" });
  };
  confirmCancellation = async (): Promise<void> => {
    if (!this.#active || this.#state.stage !== "confirming") return;
    const observed = this.#state.job;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "cancelling", job: observed, failure: null });
    try {
      const result = await this.actions.requestCancellation.execute(
        this.access,
        observed,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", job: result.value, failure: null }
          : { stage: "unconfirmed", job: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unconfirmed",
          job: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: JobDetailState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
