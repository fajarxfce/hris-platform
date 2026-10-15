import type { PayrollAmendmentReviewAction } from "../../domain/entities/payroll-amendment-review-details";

export function payrollAmendmentReviewsPath(
  company: string,
  amendment: string,
  parameters = new URLSearchParams(),
): string {
  const query = new URLSearchParams(parameters);
  query.set("company", company);
  for (const key of ["revision", "historyAfter", "assessment", "tab"]) query.delete(key);
  return `/payroll/amendments/${encodeURIComponent(amendment)}/reviews?${query}`;
}
export function payrollAmendmentReviewPath(
  company: string,
  review: string,
  parameters = new URLSearchParams(),
): string {
  const query = new URLSearchParams(parameters);
  query.set("company", company);
  for (const key of ["revision", "historyAfter", "assessment"]) query.delete(key);
  return `/payroll/amendment-reviews/${encodeURIComponent(review)}?${query}`;
}
export function payrollAmendmentReviewSubmissionPath(
  company: string,
  amendment: string,
  parameters = new URLSearchParams(),
): string {
  return payrollAmendmentReviewsPath(company, amendment, parameters).replace(
    "/reviews?",
    "/reviews/new?",
  );
}
export function payrollAmendmentReviewActionPath(
  company: string,
  review: string,
  action: PayrollAmendmentReviewAction,
  parameters = new URLSearchParams(),
): string {
  const query = new URLSearchParams(parameters);
  query.delete("tab");
  return payrollAmendmentReviewPath(company, review, query).replace(
    "?",
    `/${action.toLowerCase()}?`,
  );
}
