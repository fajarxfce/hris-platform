import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { ClientPolicyChange } from "../../domain/entities/client-policy-change";
import type {
  ClientPolicyChangeDto,
  ClientPolicyReceiptDto,
} from "../models/client-policy-change-dto";

export function toClientPolicyChangeDto(input: ClientPolicyChange): ClientPolicyChangeDto {
  return {
    expectedVersion: input.expectedVersion,
    activateAt: input.activateAt,
    disabledModules: [...input.disabledModules],
    minimumBuilds: {
      android: input.minimumBuilds.android,
      ios: input.minimumBuilds.ios,
      web: input.minimumBuilds.web,
    },
    maintenance:
      input.maintenance === null
        ? null
        : { startsAt: input.maintenance.startsAt, endsAt: input.maintenance.endsAt },
    reason: input.reason,
  };
}
export function toClientPolicyReceipt(
  dto: ClientPolicyReceiptDto,
  companyId: CompanyId,
  expectedVersion: number | null,
): MutationReceipt {
  if (
    dto.id.toLowerCase() !== companyId.toLowerCase() ||
    dto.version !== (expectedVersion ?? -1) + 1
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
