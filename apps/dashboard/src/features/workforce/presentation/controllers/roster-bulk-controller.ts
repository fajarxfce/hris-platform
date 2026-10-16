import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { RosterBulkChange } from "../../domain/entities/roster-bulk-change";
import type { RosterBulkSelection } from "../../domain/entities/roster-bulk-selection";
import { canManageCalendars } from "../../domain/policies/calendar-policy";
import {
  rosterBulkAccessLost,
  rosterBulkCommandRejected,
  rosterBulkReviewChanged,
} from "../../domain/policies/roster-bulk-policy";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { initialRosterBulkState, type RosterBulkState } from "../models/roster-bulk-state";

type Submission = Readonly<{ operation: OperationId; change: RosterBulkChange }>;

/** Owns one preview and its original submission until acknowledgement or explicit departure. */
export class RosterBulkController {
  #state: RosterBulkState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<WorkforceUseCases, "reviewRosterBulk" | "applyRosterBulk">,
    private readonly access: CompanyAccess,
    private readonly nextIdentifier: () => string,
  ) {
    this.#state = canManageCalendars(access.permissions)
      ? initialRosterBulkState
      : {
          ...initialRosterBulkState,
          stage: "unavailable",
          failure: { code: "access_denied", fields: {}, parameters: {} },
        };
  }
  getSnapshot = (): RosterBulkState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    if (this.#active) return;
    this.#active = true;
    this.refresh();
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    this.#submission = null;
    this.publish(initialRosterBulkState);
  };
  refresh = (): void => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    this.#pending = null;
    this.publish(
      canManageCalendars(this.access.permissions)
        ? initialRosterBulkState
        : {
            ...initialRosterBulkState,
            stage: "unavailable",
            failure: { code: "access_denied", fields: {}, parameters: {} },
          },
    );
  };
  prepare = async (selection: RosterBulkSelection): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing" || this.#submission) return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...initialRosterBulkState, stage: "preparing" });
    try {
      const result = await this.actions.reviewRosterBulk.execute(
        this.access,
        selection,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...this.#state, stage: "reviewing", review: result.value }
          : {
              ...this.#state,
              stage: rosterBulkAccessLost(result.failure) ? "unavailable" : "editing",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...this.#state,
          stage: "editing",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  revise = (): void => {
    if (this.#active && !this.#submission && ["reviewing", "conflict"].includes(this.#state.stage))
      this.publish(initialRosterBulkState);
  };
  save = async (reason: string): Promise<void> => {
    const review = this.#state.review;
    if (
      !this.#active ||
      this.#state.stage !== "reviewing" ||
      !review ||
      review.changeCount === 0 ||
      review.companyId !== this.access.companyId
    )
      return;
    try {
      this.#submission = Object.freeze({
        operation: this.nextIdentifier() as OperationId,
        change: Object.freeze({
          companyId: review.companyId,
          selection: review.selection,
          reviewRevision: review.revision,
          reason: reason.trim(),
        }),
      });
    } catch {
      this.publish({
        ...this.#state,
        failure: { code: "unexpected_error", fields: {}, parameters: {} },
      });
      return;
    }
    await this.submit(false);
  };
  retry = async (): Promise<void> => {
    if (this.#active && this.#state.stage === "unconfirmed") await this.submit(true);
  };
  private async submit(wasUnconfirmed: boolean): Promise<void> {
    const submission = this.#submission;
    if (!submission) return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({
      ...this.#state,
      stage: "saving",
      failure: null,
      operationId: submission.operation,
    });
    try {
      let result: Result<MutationReceipt>;
      try {
        result = await this.actions.applyRosterBulk.execute(
          this.access,
          submission.operation,
          submission.change,
          pending.signal,
        );
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: "saved",
          failure: null,
          receipt: result.value,
          operationId: null,
        });
      } else {
        const review = rosterBulkAccessLost(result.failure) ? null : this.#state.review;
        if (!wasUnconfirmed && rosterBulkCommandRejected(result.failure)) {
          this.#submission = null;
          this.publish({
            ...this.#state,
            review,
            failure: result.failure,
            operationId: null,
            stage:
              review === null
                ? "unavailable"
                : rosterBulkReviewChanged(result.failure)
                  ? "conflict"
                  : "reviewing",
          });
        } else
          this.publish({ ...this.#state, review, stage: "unconfirmed", failure: result.failure });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: RosterBulkState): void {
    this.#state = state;
    for (const listener of [...this.#listeners]) listener();
  }
}
