import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { LifecycleCaseChange } from "../../domain/entities/lifecycle-case-change";
import type {
  LifecycleCaseChangeDto,
  LifecycleCaseReceiptDto,
} from "../models/lifecycle-case-change-dto";

export function toLifecycleCaseChangeDto(change: LifecycleCaseChange): LifecycleCaseChangeDto {
  return { expectedVersion: change.expectedVersion, reason: change.reason };
}
/** Task and case transitions advance the same aggregate by exactly one version. */
export function toLifecycleCaseReceipt(
  dto: LifecycleCaseReceiptDto,
  change: Pick<LifecycleCaseChange, "caseId" | "expectedVersion">,
): MutationReceipt {
  if (dto.id.toLowerCase() !== change.caseId || dto.version !== change.expectedVersion + 1)
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
