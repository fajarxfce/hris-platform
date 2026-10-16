import { isUuid } from "../../../../core/domain/identifiers";
import { managedAccountContext } from "./managed-account-route";

export function oidcBindingContext(parameters: URLSearchParams): string {
  const context = new URLSearchParams(managedAccountContext(parameters));
  const after = parameters.get("bindingAfter");
  if (after && isUuid(after)) context.set("bindingAfter", after.toLowerCase());
  return context.toString();
}
