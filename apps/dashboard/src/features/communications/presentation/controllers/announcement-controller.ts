import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { CommunicationsUseCases } from "../contracts/communications-use-cases";
import { type AnnouncementState, initialAnnouncementState } from "../models/announcement-state";

export class AnnouncementController {
  #state = initialAnnouncementState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: CommunicationsUseCases["loadAnnouncement"],
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly version: string | null,
  ) {}
  getSnapshot = (): AnnouncementState => this.#state;
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
    this.publish(initialAnnouncementState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...this.#state, stage: "loading", failure: null });
    try {
      const result = await this.load.execute(this.access, this.id, this.version, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", announcement: result.value, failure: null }
          : { stage: "unavailable", announcement: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          announcement: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: AnnouncementState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
