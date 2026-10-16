import type { Result } from "../../../../core/domain/result";
import type { ManagedAccountPage } from "../../domain/entities/managed-account-page";

export type ManagedAccountsQuery<Access> = Readonly<{
  execute(
    access: Access,
    query: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<ManagedAccountPage>>;
}>;
