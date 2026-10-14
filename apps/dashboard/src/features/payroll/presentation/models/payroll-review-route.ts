import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollReviewAction } from "../../domain/entities/payroll-review-command";
export const payrollReviewsPath = (company: CompanyId, run: string) =>
  `/payroll/runs/${encodeURIComponent(run)}/reviews?company=${company}`;
export const payrollReviewSubmissionPath = (company: CompanyId, run: string) =>
  `/payroll/runs/${encodeURIComponent(run)}/reviews/new?company=${company}`;
export const payrollReviewPath = (company: CompanyId, id: string) =>
  `/payroll/reviews/${encodeURIComponent(id)}?company=${company}`;
export const payrollReviewActionPath = (
  company: CompanyId,
  id: string,
  action: PayrollReviewAction,
) => `/payroll/reviews/${encodeURIComponent(id)}/${action.toLowerCase()}?company=${company}`;
