import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ApprovalTemplateSearch } from "../../domain/entities/approval-template";

export function approvalTemplateSearch(
  parameters: URLSearchParams,
  company: CompanyId,
  today: string,
): ApprovalTemplateSearch {
  return Object.freeze({
    kind: parameters.get("kind") ?? "LEAVE",
    asOf: parameters.get("asOf") ?? today,
    after: parameters.get("company") === company ? parameters.get("after") : null,
  });
}
export function approvalTemplateParameters(
  company: CompanyId,
  search: ApprovalTemplateSearch,
  revision?: string | null,
): URLSearchParams {
  const parameters = new URLSearchParams({ company, kind: search.kind, asOf: search.asOf });
  if (search.after !== null) parameters.set("after", search.after);
  if (revision != null) parameters.set("revision", revision);
  return parameters;
}
