import { isUuid } from "../../../../core/domain/identifiers";
import type { PlatformAccess } from "../../../identity/domain/entities/platform-access";

export function canReadIntegrationConfiguration(access: PlatformAccess): boolean {
  return isUuid(access.accountId) && access.permissions.includes("identity.manage");
}
