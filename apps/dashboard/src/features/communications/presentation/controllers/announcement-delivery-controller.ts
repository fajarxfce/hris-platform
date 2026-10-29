import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { CommunicationsUseCases } from "../contracts/communications-use-cases";
import {
  type AnnouncementDeliveryState,
  initialAnnouncementDeliveryState,
} from "../models/announcement-delivery-state";

export class AnnouncementDeliveryController {
  #state = initialAnnouncementDeliveryState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: CommunicationsUseCases["loadDelivery"],
    private readonly access: CompanyAccess,
    private readonly id: string,
  ) {}
  getSnapshot = (): AnnouncementDeliveryState => this.#state;
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
    this.publish(initialAnnouncementDeliveryState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "loading", delivery: null, failure: null });
    try {
      const result = await this.load.execute(this.access, this.id, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", delivery: result.value, failure: null }
          : { stage: "unavailable", delivery: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          delivery: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: AnnouncementDeliveryState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
