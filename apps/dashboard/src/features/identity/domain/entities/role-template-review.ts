import type { RoleTemplate } from "./role-template";
import type { RoleTemplateRevision } from "./role-template-revision";
export type RoleTemplateReview = Readonly<{
  current: RoleTemplate;
  history: Readonly<{ items: readonly RoleTemplateRevision[]; nextCursor: string | null }>;
}>;
