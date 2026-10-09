export type AuditSearchDto = Readonly<{
  from: string | null;
  until: string | null;
  cursor: string | null;
  actorId: string | null;
  resourceType: string | null;
  resourceId: string | null;
  action: string | null;
  limit: number;
}>;
