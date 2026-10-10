import type { CompanyModule } from "./company-module";
import type { MaintenanceWindow } from "./maintenance-window";
import type { MinimumClientBuilds } from "./minimum-client-builds";

export type ClientPolicyChange = Readonly<{
  expectedVersion: number | null;
  activateAt: string | null;
  disabledModules: readonly CompanyModule[];
  minimumBuilds: MinimumClientBuilds;
  maintenance: MaintenanceWindow | null;
  reason: string;
}>;
