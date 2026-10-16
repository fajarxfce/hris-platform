import type { CompanyId } from "../../../../core/domain/identifiers";
import { managedAccountContext } from "./managed-account-route";

export function companyAccountContext(company: CompanyId, parameters: URLSearchParams): string {
  const context = new URLSearchParams(managedAccountContext(parameters));
  context.set("company", company);
  return context.toString();
}
