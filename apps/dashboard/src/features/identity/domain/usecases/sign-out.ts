import type { IdentityRepository } from "../repositories/identity-repository";

export class SignOut {
  constructor(private readonly identities: IdentityRepository) {}
  execute(signal: AbortSignal) {
    return this.identities.signOut(signal);
  }
}
