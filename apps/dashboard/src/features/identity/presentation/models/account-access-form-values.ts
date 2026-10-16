import type { ManagedAccountReview } from "../../domain/entities/managed-account-review";
export type AccountAccessFormValues = {
  active: boolean;
  platformPermissions: string[];
  reason: string;
};
export function accountAccessFormValues(
  review: ManagedAccountReview | null,
): AccountAccessFormValues {
  return {
    active: review?.account.active ?? false,
    platformPermissions: [...(review?.account.platformPermissions ?? [])],
    reason: "",
  };
}
