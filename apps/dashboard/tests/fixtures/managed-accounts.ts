import type {
  ManagedAccountDto,
  ManagedAccountReviewDto,
} from "../../src/features/identity/data/models/managed-account-dto";

export const platformActorId = "20000000-0000-4000-8000-000000000001";
export const platformPermissions = ["companies.create", "identity.manage"];
export function managedAccountId(index = 1): string {
  return `32000000-0000-4000-8000-${index.toString().padStart(12, "0")}`;
}
export function managedAccount(index = 1): ManagedAccountDto {
  return {
    id: managedAccountId(index),
    email: `colleague${index}@example.invalid`,
    displayName: `Example Colleague ${index}`,
    active: true,
    invitationPending: false,
    mfaConfigured: false,
    platformPermissions: ["companies.create"],
    version: 4,
  };
}
export function managedAccountReview(index = 1): ManagedAccountReviewDto {
  return { account: managedAccount(index), assignablePermissions: [...platformPermissions] };
}
