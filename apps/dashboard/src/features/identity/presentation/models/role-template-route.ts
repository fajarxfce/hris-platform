export function roleTemplateParameters(
  company: string,
  parameters: URLSearchParams,
): URLSearchParams {
  const result = new URLSearchParams({ company });
  if (
    (!parameters.has("company") || parameters.get("company") === company) &&
    parameters.has("after")
  )
    result.set("after", parameters.get("after") ?? "");
  return result;
}
