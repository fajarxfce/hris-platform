import type { ClientPolicyRevision } from "./client-policy-revision";
import type { ClientPolicySettings } from "./client-policy-settings";

export type ClientPolicyReview = Readonly<{
  settings: ClientPolicySettings;
  selected: ClientPolicyRevision | null;
}>;
