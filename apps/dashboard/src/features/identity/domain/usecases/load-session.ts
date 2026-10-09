import type { IdentityRepository } from "../repositories/identity-repository";

export class LoadSession {
  constructor(private readonly identities: IdentityRepository) {}
  execute(signal: AbortSignal) {
    return this.identities.session(signal);
  }
}
