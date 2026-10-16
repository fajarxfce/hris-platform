import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "./fixtures/admin-company";

test("real API role revisions recover one lost save and leave applied member access unchanged", async ({
  page,
  context,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-role-admin@example.invalid",
    "BROWSERROLES",
    "Browser roles",
  );
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  await page.getByRole("link", { name: "Company roles", exact: true }).click();
  await page.getByRole("link", { name: "Create role", exact: true }).click();
  await page.getByLabel("Code", { exact: true }).fill("people_review");
  await page.getByLabel("Name", { exact: true }).fill("People reviewer");
  const permissions = page.getByRole("region", { name: "Permissions", exact: true });
  await permissions.locator("summary").filter({ hasText: "Company" }).click();
  await page.getByRole("checkbox", { name: "View company", exact: true }).check();
  await permissions.locator("summary").filter({ hasText: "People" }).click();
  await page.getByRole("checkbox", { name: "View employees", exact: true }).check();
  await page.getByLabel("Reason", { exact: true }).fill("Initial reviewed responsibilities");
  await page.getByRole("button", { name: "Save role", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Role saved.");
  await page.getByRole("link", { name: "View role", exact: true }).click();
  const id = new URL(page.url()).pathname.split("/").at(-1);
  expect(id).toMatch(/^[0-9a-f-]{36}$/u);
  const endpoint = `/api/v1/companies/${company}/role-templates/${id}`;
  const original = await (await context.request.get(endpoint)).json();
  expect(original).toMatchObject({
    current: { code: "PEOPLE_REVIEW", version: 0 },
    history: { items: [{ revision: 0 }] },
  });
  const member = "a0000000-0000-4000-8000-000000000064";
  const memberPath = `/api/v1/companies/${company}/members/${member}`;
  const assignment = await context.request.put(memberPath, {
    headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
    data: {
      expectedVersion: null,
      active: true,
      permissions: [],
      roleTemplates: [{ id, version: 0 }],
      reason: "Reviewed member responsibilities",
    },
  });
  expect(assignment.status()).toBe(200);
  const before = await (await context.request.get(memberPath)).json();
  expect(before).toMatchObject({
    member: { permissions: ["company.read", "people.read"] },
    roleTemplates: [{ version: 0 }],
  });
  await page.getByRole("link", { name: "Edit role", exact: true }).click();
  await expect(page.getByLabel("Code", { exact: true })).toHaveAttribute("readonly", "");
  await page.getByLabel("Name", { exact: true }).fill("Team reviewer");
  await page
    .getByRole("region", { name: "Permissions", exact: true })
    .locator("summary")
    .filter({ hasText: "People" })
    .click();
  await page.getByRole("checkbox", { name: "View employees", exact: true }).uncheck();
  await page.getByRole("checkbox", { name: "View team employees", exact: true }).check();
  await page.getByLabel("Reason", { exact: true }).fill("Restrict new assignments to team access");
  const commands: { operation: string | undefined; body: unknown }[] = [];
  await page.route(`**${endpoint}`, async (route) => {
    if (route.request().method() !== "PUT") return route.fallback();
    commands.push({
      operation: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    if (commands.length === 1) return route.abort("failed");
    return route.fulfill({ response });
  });
  await page.getByRole("button", { name: "Save role", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Role saved.");
  expect(commands).toHaveLength(2);
  expect(commands[0]).toEqual(commands[1]);
  expect(await (await context.request.get(memberPath)).json()).toEqual(before);
  const current = await (await context.request.get(endpoint)).json();
  expect(current.history.items).toHaveLength(2);
  expect(current.history.items[1]).toEqual(original.history.items[0]);
  expect(current.current).toMatchObject({
    version: 1,
    name: "Team reviewer",
    permissions: ["company.read", "people.team.read"],
  });
  await page.getByRole("link", { name: "View role", exact: true }).click();
  await page.getByRole("button", { name: "View revision: Revision 0", exact: true }).click();
  await expect(page.getByRole("region", { name: "Revision 0", exact: true })).toContainText(
    "People reviewer",
  );
  await page.unroute(`**${endpoint}`);
  await page.getByRole("link", { name: "Edit role", exact: true }).click();
  await page.getByRole("checkbox", { name: "Active", exact: true }).uncheck();
  await page.getByLabel("Reason", { exact: true }).fill("Retire template for new assignments");
  await page.getByRole("button", { name: "Save role", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Role saved.");
  const archived = await (await context.request.get(endpoint)).json();
  expect(archived.current).toMatchObject({ active: false, version: 2 });
  expect(archived.history.items).toHaveLength(3);
  expect(await (await context.request.get(memberPath)).json()).toEqual(before);
  const denied = await context.request.put(memberPath, {
    headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
    data: {
      expectedVersion: 0,
      active: true,
      permissions: [],
      roleTemplates: [{ id, version: 2 }],
      reason: "Reject inactive role assignment",
    },
  });
  expect(denied.status()).toBe(422);
  expect((await denied.json()).code).toBe("role_template_unavailable");
});
