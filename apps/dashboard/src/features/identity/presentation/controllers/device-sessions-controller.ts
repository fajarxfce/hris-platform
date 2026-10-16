import { failed, type Result } from "../../../../core/domain/result";
import type { DeviceSession } from "../../domain/entities/device-session";
import type { DeviceSessionUseCases } from "../contracts/device-session-use-cases";
import {
  type DeviceSessionsState,
  initialDeviceSessionsState,
} from "../models/device-sessions-state";

/** One account owns one read or revocation; an uncertain DELETE retains its exact session. */
export class DeviceSessionsController {
  #state = initialDeviceSessionsState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(private readonly actions: DeviceSessionUseCases) {}
  getSnapshot = (): DeviceSessionsState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    if (this.#active) return;
    this.#active = true;
    if (this.#state.stage !== "unavailable") void this.refresh();
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    const failure = this.#state.failure;
    // Re-admitting the same account cannot silently loop on a rejected credential.
    this.publish(
      failure &&
        ["authentication_required", "session_revoked", "unauthenticated"].includes(failure.code)
        ? { ...initialDeviceSessionsState, stage: "unavailable", failure }
        : initialDeviceSessionsState,
    );
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || ["confirming", "revoking"].includes(this.#state.stage)) return;
    const selected = this.#state.selected;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...initialDeviceSessionsState, selected });
    let result: Result<readonly DeviceSession[]>;
    try {
      result = await this.actions.loadDeviceSessions.execute(pending.signal);
    } catch {
      result = failed("unexpected_error");
    }
    if (pending.signal.aborted || this.#pending !== pending) return;
    this.#pending = null;
    if (result.ok) {
      this.publish({
        ...initialDeviceSessionsState,
        stage: "ready",
        sessions: result.value,
        ended: selected !== null && !result.value.some((item) => item.id === selected.id),
      });
    } else {
      const revoked = ["authentication_required", "session_revoked", "unauthenticated"].includes(
        result.failure.code,
      );
      this.publish({
        ...initialDeviceSessionsState,
        stage: selected && !revoked ? "unconfirmed" : "unavailable",
        selected: revoked ? null : selected,
        failure: result.failure,
      });
    }
  };
  review = (id: string): void => {
    if (!this.#active || this.#state.stage !== "ready") return;
    const selected = this.#state.sessions?.find((item) => item.id === id);
    if (selected) this.publish({ ...this.#state, stage: "confirming", selected, ended: false });
  };
  dismissReview = (): void => {
    if (this.#state.stage === "confirming")
      this.publish({ ...this.#state, stage: "ready", selected: null });
  };
  revoke = async (): Promise<void> => {
    const selected = this.#state.selected;
    if (!this.#active || !selected || !["confirming", "unconfirmed"].includes(this.#state.stage))
      return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...this.#state, stage: "revoking", failure: null });
    let result: Result<null>;
    try {
      result = await this.actions.revokeDeviceSession.execute(selected.id, pending.signal);
    } catch {
      result = failed("unexpected_error");
    }
    if (pending.signal.aborted || this.#pending !== pending) return;
    this.#pending = null;
    if (result.ok) {
      this.publish({
        ...initialDeviceSessionsState,
        stage: "ready",
        sessions:
          this.#state.sessions === null
            ? null
            : Object.freeze(this.#state.sessions.filter((item) => item.id !== selected.id)),
        ended: true,
      });
    } else if (
      ["authentication_required", "session_revoked", "unauthenticated"].includes(
        result.failure.code,
      )
    ) {
      this.publish({
        ...initialDeviceSessionsState,
        stage: "unavailable",
        failure: result.failure,
      });
    } else {
      this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    }
  };
  private publish(state: DeviceSessionsState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
