import type { ManagedAccount } from "./managed-account";
export type ManagedAccountPage = Readonly<{
  items: readonly ManagedAccount[];
  nextCursor: string | null;
}>;
