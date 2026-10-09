import type { JobSearch } from "../../domain/entities/job-search";

export function jobSearchParameters(
  search: JobSearch,
  companyId: string,
  jobId: string | null = null,
): URLSearchParams {
  const parameters = new URLSearchParams({ company: companyId });
  if (search.beforeAt !== null) parameters.set("beforeAt", search.beforeAt);
  if (search.beforeId !== null) parameters.set("beforeId", search.beforeId);
  if (jobId !== null) parameters.set("job", jobId);
  return parameters;
}
