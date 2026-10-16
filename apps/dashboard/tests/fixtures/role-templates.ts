import type { RoleTemplateReviewDto } from "../../src/features/identity/data/models/role-template-dto";
export const roleTemplateId = (index = 1) =>
  `d1000000-0000-4000-8000-${index.toString().padStart(12, "0")}`;
export const roleCatalog = [
  "company.read",
  "identity.manage",
  "people.read",
  "people.team.read",
  "leave.read",
  "leave.manage",
  "payroll.read",
];
export function roleTemplateReview(index = 1, version = 1): RoleTemplateReviewDto {
  const fields = {
    code: `REVIEWER_${index}`,
    name: `Operations reviewer ${index}`,
    permissions: ["company.read", "people.read"],
    active: true,
  };
  return {
    current: { ...fields, id: roleTemplateId(index), version },
    history: {
      items: Array.from({ length: version + 1 }, (_, offset) => ({
        ...fields,
        permissions: [...fields.permissions],
        revision: version - offset,
        reason: `Reviewed access revision ${version - offset}`,
        actorId: "20000000-0000-4000-8000-000000000001",
        recordedAt: "2026-10-10T03:00:00Z",
      })),
      nextCursor: null,
    },
  };
}
export function roleTemplateHistoryPage(
  review: RoleTemplateReviewDto,
  after: string | null = null,
): RoleTemplateReviewDto {
  const remaining = review.history.items.filter(
    (item) => after === null || item.revision < Number(after),
  );
  const items = remaining.slice(0, 20);
  return {
    current: review.current,
    history: {
      items,
      nextCursor: remaining.length > items.length ? String(items.at(-1)?.revision) : null,
    },
  };
}
