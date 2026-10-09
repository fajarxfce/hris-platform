import { failed, type Result } from "../../../../core/domain/result";
import type { SignInCredentials } from "../entities/sign-in";
import type { IdentityRepository } from "../repositories/identity-repository";

export class SignIn {
  constructor(private readonly identities: IdentityRepository) {}

  async execute(input: SignInCredentials, signal: AbortSignal): Promise<Result<void>> {
    const email = input.email.trim().toLowerCase();
    if (
      !/^[^\s@]+@[^\s@]+\.[^\s@]+$/u.test(email) ||
      email.length > 254 ||
      input.password.length < 1 ||
      input.password.length > 1024
    ) {
      return failed("invalid_credentials_input");
    }
    return this.identities.signIn({ email, password: input.password }, signal);
  }
}
