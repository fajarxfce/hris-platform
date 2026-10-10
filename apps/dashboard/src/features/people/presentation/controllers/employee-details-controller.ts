import type { Failure } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import {
  type EmployeeDetailsState,
  initialEmployeeDetailsState,
} from "../models/employee-details-state";

export class EmployeeDetailsController {
  #state: EmployeeDetailsState = initialEmployeeDetailsState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: PeopleUseCases["loadEmployee"],
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly asOf: string,
  ) {}
  getSnapshot = (): EmployeeDetailsState => this.#state;
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
    this.publish(initialEmployeeDetailsState);
  };
  /** A child read can invalidate the enclosing company/session, without owning this controller. */
  reportScopeFailure = (failure: Failure): void => {
    if (
      !this.#active ||
      ![
        "company_access_denied",
        "authentication_required",
        "session_revoked",
        "mfa_required",
      ].includes(failure.code)
    )
      return;
    this.#pending?.abort();
    this.#pending = null;
    this.publish({ stage: "unavailable", employee: null, failure });
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "loading", employee: null, failure: null });
    try {
      const result = await this.load.execute(this.access, this.id, this.asOf, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", employee: result.value, failure: null }
          : { stage: "unavailable", employee: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          employee: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: EmployeeDetailsState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
