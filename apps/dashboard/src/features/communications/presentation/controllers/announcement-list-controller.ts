import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { CommunicationsUseCases } from "../contracts/communications-use-cases";
import {
  type AnnouncementListState,
  initialAnnouncementListState,
} from "../models/announcement-list-state";

export class AnnouncementListController {
  #state = initialAnnouncementListState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<CommunicationsUseCases, "loadAnnouncements" | "loadHistory">,
    private readonly access: CompanyAccess,
    private readonly historyId: string | null,
    private readonly after: string | null,
  ) {}
  getSnapshot = (): AnnouncementListState => this.#state;
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
    this.publish(initialAnnouncementListState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...this.#state, stage: "loading", failure: null });
    try {
      const result =
        this.historyId === null
          ? await this.actions.loadAnnouncements.execute(this.access, this.after, pending.signal)
          : await this.actions.loadHistory.execute(
              this.access,
              this.historyId,
              this.after,
              pending.signal,
            );
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
  private publish(state: AnnouncementListState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
