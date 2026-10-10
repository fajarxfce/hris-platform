import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LifecycleTaskDefinition } from "./lifecycle-task-definition";

export const lifecycleKinds = ["ONBOARDING", "OFFBOARDING"] as const;
export type LifecycleKind = (typeof lifecycleKinds)[number];
export type LifecycleTemplateId = string & { readonly lifecycleTemplateId: unique symbol };
export type LifecycleTemplate = Readonly<{
  id: LifecycleTemplateId;
  companyId: CompanyId;
  code: string;
  name: string;
  kind: LifecycleKind;
  active: boolean;
  version: number;
  tasks: readonly LifecycleTaskDefinition[];
}>;
