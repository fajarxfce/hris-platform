import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { CompanyModule } from "./company-module";
import type { MaintenanceWindow } from "./maintenance-window";
import type { MinimumClientBuilds } from "./minimum-client-builds";

export type ClientPolicyRevision = Readonly<{
  companyId: CompanyId;
  version: number;
  activateAt: string;
  disabledModules: readonly CompanyModule[];
  minimumBuilds: MinimumClientBuilds;
  maintenance: MaintenanceWindow | null;
  recordedAt: string;
  actorId: AccountId;
  reason: string;
}>;
