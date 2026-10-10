import type { CompanyId } from "../../../../core/domain/identifiers";

export function employeeImportParameters(
  company: CompanyId,
  after: string | null,
): URLSearchParams {
  const parameters = new URLSearchParams({ company });
  if (after !== null) parameters.set("after", after);
  return parameters;
}
