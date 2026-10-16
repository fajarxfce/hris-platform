export type RoleTemplateRevision = Readonly<{
  revision: number;
  code: string;
  name: string;
  permissions: readonly string[];
  active: boolean;
  actorId: string;
  reason: string;
  recordedAt: string;
}>;
