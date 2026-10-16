import { expect, it, vi } from "vitest";
import {
  roleCatalog,
  roleTemplateHistoryPage,
  roleTemplateId,
  roleTemplateReview,
} from "../../../../tests/fixtures/role-templates";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { RoleTemplateChange } from "../domain/entities/role-template-change";
import { createIdentityFeature } from "./identity-feature";

const access = {
  companyId: "10000000-0000-4000-8000-000000000001" as CompanyId,
  permissions: ["identity.manage"],
};
const operation = "d2000000-0000-4000-8000-000000000001" as OperationId;
const signal = () => new AbortController().signal;
const change: RoleTemplateChange = {
  id: roleTemplateId(),
  expectedVersion: null,
  code: "REVIEWER",
  name: "Operations reviewer",
  permissions: ["people.read", "company.read"],
  active: true,
  reason: "Approved responsibilities",
};

it("validates scope, identifiers and finite cursors before role I/O", async () => {
  const request = vi.fn<HttpClient["request"]>();
  const f = createIdentityFeature({ request });
  const denied = { ...access, permissions: ["people.read"] };
  for (const result of [
    await f.loadRoleTemplates.execute(denied, null, signal()),
    await f.loadRoleTemplate.execute(denied, roleTemplateId(), null, signal()),
    await f.loadAssignablePermissions.execute(denied, signal()),
    await f.saveRoleTemplate.execute(denied, operation, change, signal()),
  ])
    expect(result).toMatchObject({ ok: false, failure: { code: "access_denied" } });
  for (const after of ["-1", "1.5", "9007199254740992", "01", "../other"])
    expect(
      await f.loadRoleTemplate.execute(access, roleTemplateId(), after, signal()),
    ).toMatchObject({ ok: false });
  expect(await f.loadRoleTemplates.execute(access, "../other", signal())).toMatchObject({
    ok: false,
  });
  expect(await f.loadRoleTemplate.execute(access, "../other", null, signal())).toMatchObject({
    ok: false,
  });
  expect(
    await f.loadAssignablePermissions.execute(
      { ...access, companyId: "../other" as CompanyId },
      signal(),
    ),
  ).toMatchObject({ ok: false });
  expect(
    await f.saveRoleTemplate.execute(access, "invalid" as OperationId, change, signal()),
  ).toMatchObject({ ok: false });
  const aborted = new AbortController();
  aborted.abort();
  expect(() => f.loadRoleTemplates.execute(access, null, aborted.signal)).toThrow();
  expect(request).not.toHaveBeenCalled();
});
it("maps detached catalog pages and contiguous revision history through scoped endpoints", async () => {
  const raw = roleTemplateReview(1, 21);
  const records = Array.from({ length: 20 }, (_, index) => roleTemplateReview(index + 1).current);
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValueOnce(roleCatalog)
    .mockResolvedValueOnce({ items: records, nextCursor: records.at(-1)?.id })
    .mockResolvedValueOnce(roleTemplateHistoryPage(raw))
    .mockResolvedValueOnce(roleTemplateHistoryPage(raw, "2"));
  const f = createIdentityFeature({ request });
  const catalog = await f.loadAssignablePermissions.execute(access, signal());
  expect(catalog).toEqual({ ok: true, value: [...roleCatalog].sort() });
  const page = await f.loadRoleTemplates.execute(access, null, signal());
  if (!page.ok) throw new Error("Invalid page fixture");
  expect(page.value.items[0]?.companyId).toBe(access.companyId);
  expect(Object.isFrozen(page.value.items[0]?.permissions)).toBe(true);
  expect(request.mock.calls[1]?.[0].path).toBe(
    `/api/v1/companies/${access.companyId}/role-templates?limit=20`,
  );
  const review = await f.loadRoleTemplate.execute(
    access,
    roleTemplateId().toUpperCase(),
    null,
    signal(),
  );
  if (!review.ok) throw new Error("Invalid review fixture");
  expect(review.value.history.nextCursor).toBe("2");
  expect(Object.isFrozen(review.value.history.items)).toBe(true);
  raw.history.items[0]?.permissions.push("payroll.read");
  expect(review.value.history.items[0]?.permissions).toEqual(["company.read", "people.read"]);
  const older = await f.loadRoleTemplate.execute(access, roleTemplateId(), "2", signal());
  expect(older).toMatchObject({
    ok: true,
    value: {
      current: { version: 21 },
      history: { items: [{ revision: 1 }, { revision: 0 }], nextCursor: null },
    },
  });
  expect(request.mock.lastCall?.[0].path).toBe(
    `/api/v1/companies/${access.companyId}/role-templates/${roleTemplateId()}?historyLimit=20&historyAfter=2`,
  );
});
it.each([
  "wrong resource",
  "duplicate revision",
  "gap",
  "changed code",
  "mixed head",
  "missing history",
  "premature end",
  "invalid timestamp",
  "duplicate permission",
  "no progress",
])("rejects %s in acquired role history", async (mode) => {
  const raw = roleTemplateHistoryPage(roleTemplateReview(1, 21));
  const [latest, older] = raw.history.items;
  if (!latest || !older) throw new Error("Missing revision fixtures");
  switch (mode) {
    case "wrong resource":
      raw.current.id = roleTemplateId(2);
      break;
    case "duplicate revision":
      raw.history.items[1] = { ...latest };
      break;
    case "gap":
      raw.history.items.splice(1, 1);
      break;
    case "changed code":
      older.code = "OTHER";
      break;
    case "mixed head":
      latest.name = "Unrelated name";
      break;
    case "missing history":
      raw.history.items = [];
      break;
    case "premature end":
      raw.history.nextCursor = null;
      break;
    case "invalid timestamp":
      latest.recordedAt = "not a timestamp";
      break;
    case "duplicate permission":
      raw.current.permissions.push("people.read");
      break;
    case "no progress":
      raw.history.nextCursor = "21";
      break;
  }
  const f = createIdentityFeature({
    request: vi.fn<HttpClient["request"]>().mockResolvedValue(raw),
  });
  expect(await f.loadRoleTemplate.execute(access, roleTemplateId(), null, signal())).toMatchObject({
    ok: false,
  });
});
it("rejects duplicate, descending, oversize and non-progressing directories or catalogs", async () => {
  const request = vi.fn<HttpClient["request"]>();
  const f = createIdentityFeature({ request });
  const a = roleTemplateReview(1).current,
    b = roleTemplateReview(2).current;
  for (const raw of [
    { items: [a, a], nextCursor: null },
    { items: [b, a], nextCursor: null },
    { items: [a, { ...b, code: a.code }], nextCursor: null },
    { items: [a], nextCursor: a.id },
    { items: [], nextCursor: a.id },
    {
      items: Array.from({ length: 21 }, (_, i) => roleTemplateReview(i + 1).current),
      nextCursor: null,
    },
  ]) {
    request.mockResolvedValueOnce(raw);
    expect(await f.loadRoleTemplates.execute(access, null, signal())).toMatchObject({ ok: false });
  }
  request.mockResolvedValueOnce({ items: [a], nextCursor: null });
  expect(await f.loadRoleTemplates.execute(access, a.id, signal())).toMatchObject({ ok: false });
  for (const catalog of [
    ["people.read", "people.read"],
    Array(201).fill("people.read"),
    ["not a permission"],
  ]) {
    request.mockResolvedValueOnce(catalog);
    expect(await f.loadAssignablePermissions.execute(access, signal())).toMatchObject({
      ok: false,
    });
  }
});
it("normalizes an isolated role command and verifies its exact receipt", async () => {
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValue({ id: change.id.toUpperCase(), version: 0 });
  const f = createIdentityFeature({ request });
  expect(
    await f.saveRoleTemplate.execute(
      access,
      operation,
      {
        ...change,
        code: " reviewer ",
        name: " Operations reviewer ",
        reason: " Approved responsibilities ",
      },
      signal(),
    ),
  ).toEqual({ ok: true, value: { id: change.id, version: 0 } });
  expect(request.mock.lastCall?.[0]).toEqual({
    path: `/api/v1/companies/${access.companyId}/role-templates/${change.id}`,
    method: "PUT",
    operationId: operation,
    body: {
      code: "REVIEWER",
      name: change.name,
      permissions: ["company.read", "people.read"],
      active: true,
      expectedVersion: null,
      reason: change.reason,
    },
  });
  for (const receipt of [
    { id: roleTemplateId(2), version: 0 },
    { id: change.id, version: 1 },
    { id: change.id, version: -1 },
  ]) {
    request.mockResolvedValueOnce(receipt);
    expect(await f.saveRoleTemplate.execute(access, operation, change, signal())).toMatchObject({
      ok: false,
    });
  }
  request.mockResolvedValueOnce({ id: change.id, version: 3 });
  expect(
    await f.saveRoleTemplate.execute(
      access,
      operation,
      { ...change, expectedVersion: 2, active: false },
      signal(),
    ),
  ).toMatchObject({ ok: true, value: { version: 3 } });
});
it.each<[Partial<RoleTemplateChange>, string]>([
  [{ code: "A" }, "code"],
  [{ code: "A B" }, "code"],
  [{ code: "X".repeat(33) }, "code"],
  [{ name: " " }, "name"],
  [{ name: "X".repeat(201) }, "name"],
  [{ reason: " " }, "reason"],
  [{ reason: "X".repeat(1001) }, "reason"],
  [{ expectedVersion: 1.5 }, "expectedVersion"],
  [{ expectedVersion: Number.MAX_SAFE_INTEGER }, "expectedVersion"],
  [{ expectedVersion: NaN }, "expectedVersion"],
  [{ permissions: ["people.read", "people.read"] }, "permissions"],
  [{ permissions: ["private bad code"] }, "permissions"],
])("rejects invalid command fields before delivery", async (fields, field) => {
  const request = vi.fn<HttpClient["request"]>();
  const f = createIdentityFeature({ request });
  expect(
    await f.saveRoleTemplate.execute(access, operation, { ...change, ...fields }, signal()),
  ).toMatchObject({
    ok: false,
    failure: { code: "invalid_role_template", fields: { [field]: expect.any(String) } },
  });
  expect(request).not.toHaveBeenCalled();
});
it("preserves safe failure codes and cancellation without implicit retry", async () => {
  const request = vi.fn<HttpClient["request"]>().mockRejectedValueOnce(
    new HttpResponseError(403, {
      code: "recent_authentication_required",
      detail: "Private internal detail",
    }),
  );
  const f = createIdentityFeature({ request });
  const result = await f.saveRoleTemplate.execute(access, operation, change, signal());
  expect(result).toMatchObject({ ok: false, failure: { code: "recent_authentication_required" } });
  expect(JSON.stringify(result)).not.toContain("Private");
  const cancelled = new AbortController();
  request.mockImplementationOnce(async () => {
    cancelled.abort();
    return roleTemplateReview();
  });
  await expect(
    f.loadRoleTemplate.execute(access, roleTemplateId(), null, cancelled.signal),
  ).rejects.toMatchObject({ name: "AbortError" });
  expect(request).toHaveBeenCalledTimes(2);
});
