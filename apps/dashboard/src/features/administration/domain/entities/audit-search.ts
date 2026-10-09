export type AuditSearch = Readonly<{
  from: string | null;
  until: string | null;
  cursor: string | null;
  actorId: string | null;
  resourceType: string | null;
  resourceId: string | null;
  action: string | null;
}>;

export const defaultAuditSearch: AuditSearch = Object.freeze({
  from: null,
  until: null,
  cursor: null,
  actorId: null,
  resourceType: null,
  resourceId: null,
  action: null,
});

export const auditPageSize = 50;
