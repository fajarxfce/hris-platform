import type { CompanyModule } from "./company-module";
import type { MaintenanceWindow } from "./maintenance-window";
import type { MinimumClientBuilds } from "./minimum-client-builds";

export type EffectiveClientPolicy = Readonly<{
  version: number | null;
  enabledModules: readonly CompanyModule[];
  minimumBuilds: MinimumClientBuilds;
  maintenance: MaintenanceWindow | null;
  maintenanceActive: boolean;
  evaluatedAt: string;
  validUntil: string;
}>;
