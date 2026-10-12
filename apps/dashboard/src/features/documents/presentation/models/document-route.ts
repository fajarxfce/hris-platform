import type { CompanyId } from "../../../../core/domain/identifiers";
import type { DocumentRevisionIntent } from "../../domain/entities/document-revision-action";

export const documentsPath = "/documents";
export const documentFolderPath = (employee: string): string =>
  `${documentsPath}/employees/${encodeURIComponent(employee)}`;
export const documentPath = (id: string): string => `${documentsPath}/${encodeURIComponent(id)}`;
export const documentRevisionPath = (document: string, revision: string): string =>
  `${documentPath(document)}/revisions/${encodeURIComponent(revision)}`;
export const documentActionPath = (
  document: string,
  revision: string,
  intent: DocumentRevisionIntent,
): string => `${documentRevisionPath(document, revision)}/${intent}`;

export function documentParameters(
  company: CompanyId,
  after: string | null = null,
  query = "",
): URLSearchParams {
  const parameters = new URLSearchParams({ company });
  if (after !== null) parameters.set("after", after);
  if (query) parameters.set("query", query);
  return parameters;
}
export const documentHref = (company: CompanyId, path = documentsPath): string =>
  `${path}?${documentParameters(company)}`;
