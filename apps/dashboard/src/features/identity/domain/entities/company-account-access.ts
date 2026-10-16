import type { PlatformAccess } from "./platform-access";
import type { CompanyAccess } from "./session";

export type CompanyAccountAccess = Readonly<{
  company: CompanyAccess;
  platform: PlatformAccess;
}>;
