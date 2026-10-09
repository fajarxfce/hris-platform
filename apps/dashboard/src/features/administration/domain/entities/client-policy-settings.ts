import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ClientPolicyRevision } from "./client-policy-revision";
import type { EffectiveClientPolicy } from "./effective-client-policy";

export type ClientPolicySettings = Readonly<{
  companyId: CompanyId;
  latest: ClientPolicyRevision | null;
  effective: EffectiveClientPolicy;
}>;
