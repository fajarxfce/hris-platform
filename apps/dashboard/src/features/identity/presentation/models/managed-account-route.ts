import { isUuid } from "../../../../core/domain/identifiers";

/** Retains only bounded directory context when opening a specific account. */
export function managedAccountContext(parameters: URLSearchParams): string {
  const next = new URLSearchParams();
  const query = parameters.get("query")?.trim() ?? "";
  if (query.length > 0 && query.length <= 100) next.set("query", query);
  const after = parameters.get("after");
  if (after && isUuid(after)) next.set("after", after.toLowerCase());
  return next.toString();
}
