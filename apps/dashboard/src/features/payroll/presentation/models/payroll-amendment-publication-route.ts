export function payrollAmendmentPublicationsPath(
  company: string,
  amendment: string,
  parameters = new URLSearchParams(),
): string {
  const query = new URLSearchParams(parameters);
  query.set("company", company);
  for (const key of ["revision", "historyAfter", "assessment", "tab"]) query.delete(key);
  return `/payroll/amendments/${encodeURIComponent(amendment)}/publications?${query}`;
}
export function payrollAmendmentPublicationPath(
  company: string,
  amendment: string,
  publication: string,
  parameters = new URLSearchParams(),
): string {
  return payrollAmendmentPublicationsPath(company, amendment, parameters).replace(
    "/publications?",
    `/publications/${encodeURIComponent(publication)}?`,
  );
}
export function payrollAmendmentPublicationStartPath(
  company: string,
  amendment: string,
  parameters = new URLSearchParams(),
): string {
  return payrollAmendmentPublicationsPath(company, amendment, parameters).replace(
    "/publications?",
    "/publish?",
  );
}
export function payrollAmendmentPublicationRecoveryPath(
  company: string,
  amendment: string,
  publication: string,
  parameters = new URLSearchParams(),
): string {
  return payrollAmendmentPublicationPath(company, amendment, publication, parameters).replace(
    "?",
    "/recover?",
  );
}
