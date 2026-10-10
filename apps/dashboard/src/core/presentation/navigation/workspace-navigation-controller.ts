export type NavigationProtection = "none" | "dirty" | "pending" | "unconfirmed";
export type WorkspaceNavigationState = Readonly<{
  protection: NavigationProtection;
  deciding: boolean;
}>;
const initial: WorkspaceNavigationState = { protection: "none", deciding: false };

/** One mounted workspace owns one editor and at most one requested departure. */
export class WorkspaceNavigationController {
  #state = initial;
  #owner: string | null = null;
  #suspended = false;
  #departure: { proceed: () => void; cancel: () => void } | null = null;
  #listeners = new Set<() => void>();
  getSnapshot = (): WorkspaceNavigationState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  protect = (owner: string, protection: NavigationProtection): void => {
    if (this.#owner !== null && this.#owner !== owner)
      throw new Error("A workspace may have only one active editor");
    this.#owner = owner;
    if (protection === "none") this.stay();
    if (protection !== this.#state.protection) this.publish({ ...this.#state, protection });
  };
  release = (owner: string): void => {
    if (this.#owner !== owner) return;
    this.#owner = null;
    this.stay();
    this.publish(initial);
  };
  suspend = (suspended: boolean): void => {
    this.#suspended = suspended;
    if (suspended) this.stay();
  };
  request = (proceed: () => void, cancel: () => void = () => {}): void => {
    if (this.#departure?.proceed === proceed) return;
    if (this.#suspended || this.#departure !== null) {
      cancel();
      return;
    }
    if (this.#state.protection === "none") {
      proceed();
      return;
    }
    this.#departure = { proceed, cancel };
    this.publish({ ...this.#state, deciding: true });
  };
  leave = (): void => {
    const departure = this.#departure;
    this.#departure = null;
    if (!departure) return;
    this.publish({ ...this.#state, deciding: false });
    departure.proceed();
  };
  stay = (): void => {
    const departure = this.#departure;
    this.#departure = null;
    if (!departure) return;
    this.publish({ ...this.#state, deciding: false });
    departure.cancel();
  };
  reset = (): void => {
    this.stay();
    this.#owner = null;
    this.#suspended = false;
    this.publish(initial);
  };
  private publish(state: WorkspaceNavigationState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
