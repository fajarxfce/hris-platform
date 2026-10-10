import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LifecycleCaseId } from "../../domain/entities/lifecycle-case";
import type { OffboardingCompletion } from "../../domain/entities/offboarding-completion";
import type { OffboardingReview } from "../../domain/entities/offboarding-review";
import type { OffboardingCompletionDto, OffboardingReviewDto } from "../models/offboarding-dto";
import { toLifecycleCaseDetails } from "./lifecycle-case-mapper";

export function toOffboardingReview(
  dto: OffboardingReviewDto,
  company: CompanyId,
  id: LifecycleCaseId,
): OffboardingReview {
  const details = toLifecycleCaseDetails(dto.case, company, id);
  if (details.kind !== "OFFBOARDING" || details.status !== "OPEN" || !isCalendarDate(dto.today))
    throw new InvalidHttpResponseError();
  return Object.freeze({
    case: details,
    employmentVersion: dto.employmentVersion,
    today: dto.today,
  });
}
export function toOffboardingCompletionDto(
  change: OffboardingCompletion,
): OffboardingCompletionDto {
  return {
    expectedVersion: change.expectedVersion,
    employmentVersion: change.employmentVersion,
    reason: change.reason,
  };
}
