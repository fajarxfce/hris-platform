import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "./fixtures/admin-company";

test("real API member editing retains archived evidence, recovers a lost save and explicitly reviews reactivation", async ({
  page,
  context,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-membership-admin@example.invalid",
    "BROWSERMEMBERS",
    "Browser company access",
  );
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  const roleId = randomUUID();
  const rolePath = `/api/v1/companies/${company}/role-templates/${roleId}`;
  const member = "a0000000-0000-4000-8000-000000000066";
  const memberPath = `/api/v1/companies/${company}/members/${member}`;
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const initialRole = {
    code: "PEOPLE_REVIEW",
    name: "Team reviewer",
    permissions: ["company.read", "people.team.read"],
    active: true,
  };
  const created = await context.request.put(rolePath, {
    headers: headers(),
    data: { ...initialRole, reason: "Initial reviewed role" },
  });
  expect(created.status()).toBe(200);
  const assigned = await context.request.put(memberPath, {
    headers: headers(),
    data: {
      active: true,
      permissions: [],
      roleTemplates: [{ id: roleId, version: 0 }],
      reason: "Initial reviewed membership",
    },
  });
  expect(assigned.status()).toBe(200);
  const original = await (await context.request.get(memberPath)).json();
  const newRole = {
    ...initialRole,
    name: "Company reviewer",
    permissions: ["company.read", "people.read"],
  };
  const revised = await context.request.put(rolePath, {
    headers: headers(),
    data: { ...newRole, expectedVersion: 0, reason: "Review new assignments" },
  });
  expect(revised.status()).toBe(200);
  await page.goto(`/administration/members/${member}/edit?company=${company}`);
  await expect(page.getByText("Previously applied version: 0", { exact: true })).toBeVisible();
  await page
    .getByRole("region", { name: "Direct permissions", exact: true })
    .locator("summary")
    .filter({ hasText: "Leave" })
    .click();
  await page.getByRole("checkbox", { name: "View leave", exact: true }).check();
  await page.getByLabel("Reason", { exact: true }).fill("Keep reviewed role and add leave review");
  const archived = await context.request.put(rolePath, {
    headers: headers(),
    data: { ...newRole, active: false, expectedVersion: 1, reason: "Archive new applications" },
  });
  expect(archived.status()).toBe(200);
  const commands: { operation: string | undefined; body: unknown }[] = [];
  await page.route(`**${memberPath}`, async (route) => {
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
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is unconfirmed.");
  await page.getByRole("button", { name: "Check save outcome", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Company access saved.");
  expect(commands).toHaveLength(2);
  expect(commands[0]).toEqual(commands[1]);
  const retained = await (await context.request.get(memberPath)).json();
  expect(retained).toMatchObject({ member: { version: 1 }, directPermissions: ["leave.read"] });
  expect(retained.roleTemplates).toEqual(original.roleTemplates);
  expect(retained.member.permissions).toEqual(["company.read", "leave.read", "people.team.read"]);
  await page.unroute(`**${memberPath}`);
  await page.getByRole("link", { name: "View access", exact: true }).click();
  await page.getByRole("link", { name: "Edit company access", exact: true }).click();
  await page.getByRole("checkbox", { name: "Company access active", exact: true }).uncheck();
  await page.getByLabel("Reason", { exact: true }).fill("Pause company access");
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Company access saved.");
  expect(await (await context.request.get(memberPath)).json()).toMatchObject({
    member: { version: 2, active: false },
    roleTemplates: original.roleTemplates,
  });
  await page.getByRole("link", { name: "View access", exact: true }).click();
  await page.getByRole("link", { name: "Edit company access", exact: true }).click();
  await page.getByRole("checkbox", { name: "Company access active", exact: true }).check();
  await page.getByLabel("Reason", { exact: true }).fill("Reactivate with a current reviewed role");
  await expect(page.getByRole("button", { name: "Save access", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Replace role", exact: true }).click();
  const drawer = page.getByRole("dialog", { name: "Choose role", exact: true });
  const choose = drawer.getByRole("button", {
    name: "Use this version: Company reviewer (PEOPLE_REVIEW)",
    exact: true,
  });
  await expect(choose).toBeDisabled();
  const reopened = await context.request.put(rolePath, {
    headers: headers(),
    data: { ...newRole, active: true, expectedVersion: 2, reason: "Reopen reviewed assignments" },
  });
  expect(reopened.status()).toBe(200);
  await drawer.getByRole("button", { name: "Refresh", exact: true }).click();
  await choose.click();
  await expect(drawer).toHaveCount(0);
  await expect(page.getByText("Selected version: 3", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Company access saved.");
  const active = await (await context.request.get(memberPath)).json();
  expect(active).toMatchObject({
    member: { version: 3, active: true },
    roleTemplates: [{ id: roleId, version: 3, name: "Company reviewer" }],
  });
  expect(active.member.permissions).toEqual(["company.read", "leave.read", "people.read"]);
  const definitions = await (await context.request.get(rolePath)).json();
  expect(definitions.history.items).toHaveLength(4);
  expect(definitions.history.items[3]).toMatchObject({ revision: 0, name: "Team reviewer" });
});
