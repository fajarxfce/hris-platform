import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { OrganizationUnitChange } from "../../domain/entities/organization-unit-change";
import type {
  OrganizationChangeDto,
  OrganizationReceiptDto,
} from "../models/organization-change-dto";

export function toOrganizationChangeDto(change: OrganizationUnitChange): OrganizationChangeDto {
  return {
    code: change.code,
    name: change.name,
    kind: change.kind,
    parentId: change.parentId,
    timezone: change.timezone,
    active: change.active,
    expectedVersion: change.expectedVersion,
  };
}
export function toOrganizationReceipt(
  dto: OrganizationReceiptDto,
  change: OrganizationUnitChange,
): MutationReceipt {
  if (
    dto.id.toLowerCase() !== change.id ||
    dto.version !== (change.expectedVersion === null ? 0 : change.expectedVersion + 1)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
