import type { IdentityRepository } from "../repositories/identity-repository";

export class LoadIdentityProviders {
  constructor(private readonly identities: IdentityRepository) {}
  execute(signal: AbortSignal) {
    return this.identities.providers(signal);
  }
}
