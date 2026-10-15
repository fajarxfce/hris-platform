import type { CompanyId } from "../../../../core/domain/identifiers";
export function leaveCarryExpiryRoute(company: CompanyId, parameters: URLSearchParams, tail = "") {
  const foreignCompany = parameters.has("company") && parameters.get("company") !== company;
  const after = foreignCompany ? null : parameters.get("after");
  const resultAfter = foreignCompany ? null : parameters.get("resultAfter");
  const query = new URLSearchParams({ company });
  if (after !== null) query.set("after", after);
  const detailQuery = new URLSearchParams(query);
  if (resultAfter !== null) detailQuery.set("resultAfter", resultAfter);
  const path = "/leave/carry-expiries";
  return {
    foreignCompany,
    canonical: parameters.has("company"),
    after,
    resultAfter,
    path,
    query: query.toString(),
    detailQuery: detailQuery.toString(),
    listTo: `${path}?${query}`,
    current: `${path}${tail}?${detailQuery}`,
    processingTo: `/leave/batches?company=${encodeURIComponent(company)}`,
  };
}
