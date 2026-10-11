import { expect, test } from "@playwright/test";
import { employeeId, groupId } from "../src/features/communications/di/audience-group-fixture";
import { installAudienceGroupsApi } from "./audience-groups-api";
import { companyIds } from "./identity-api";

const directory = "/communications/audience-groups";
test("groups retain bounded directories, member pages and immutable revision navigation", async ({
  page,
}) => {
  const api = await installAudienceGroupsApi(page);
  await page.goto(directory);
  await expect(
    page.getByRole("table", { name: "Audience groups", exact: true }).getByRole("row"),
  ).toHaveCount(51);
  await page
    .getByRole("navigation", { name: "Audience group pages", exact: true })
    .getByRole("button", { name: "Next", exact: true })
    .click();
  await expect(page.getByRole("button", { name: "View: Team 51", exact: true })).toBeVisible();
  await page
    .getByRole("navigation", { name: "Audience group pages", exact: true })
    .getByRole("button", { name: "First", exact: true })
    .click();
  await page.getByRole("button", { name: "View: Field team", exact: true }).click();
  const selected = page.getByRole("table", { name: "Selected recipients", exact: true });
  await expect(selected.getByRole("row")).toHaveCount(51);
  await expect(page.getByRole("button", { name: /^Remove:/u })).toHaveCount(0);
  await page
    .getByRole("navigation", { name: "Selected recipients", exact: true })
    .getByRole("button", { name: "Next page", exact: true })
    .click();
  await expect(selected.getByText("Fictional Employee 51", { exact: true })).toBeVisible();
  await expect(selected.getByRole("row")).toHaveCount(2);
  await page.getByRole("link", { name: "Previous revision", exact: true }).click();
  await expect(page).toHaveURL(/revision=0/u);
  await expect(selected.getByText("Fictional Employee 1", { exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Edit audience group", exact: true })).toHaveCount(0);
  await page.getByRole("link", { name: "Current version", exact: true }).click();
  await expect(page.getByRole("link", { name: "Edit audience group", exact: true })).toBeVisible();
  expect(
    api.lookups.every(
      (url) => url.searchParams.has("ids") && url.searchParams.getAll("ids").length <= 50,
    ),
  ).toBe(true);
  expect(api.identity.unhandled).toEqual([]);
});
test("creation retains selected members through language changes and confirms a lost save once", async ({
  page,
}) => {
  const api = await installAudienceGroupsApi(page);
  await page.goto(directory);
  await page.getByRole("link", { name: "New audience group", exact: true }).click();
  await page.getByLabel("Name", { exact: true }).fill("Regional team");
  await page.getByRole("button", { name: "Add: Fictional Employee 1", exact: true }).click();
  await page.getByLabel("Name or code", { exact: true }).fill("EMP-51");
  await page.getByLabel("Name or code", { exact: true }).press("Enter");
  await page.getByRole("button", { name: "Add: Fictional Employee 51", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Regional coverage");
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByLabel("Nama", { exact: true })).toHaveValue("Regional team");
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-audience-group-editor.png",
    fullPage: true,
    animations: "disabled",
  });
  api.dropNext();
  await page.getByRole("button", { name: "Simpan grup", exact: true }).click();
  await expect(
    page.getByText("Hasil penyimpanan belum dapat dipastikan.", { exact: false }),
  ).toBeVisible();
  api.rejectNext("stale_version");
  await page.getByRole("button", { name: "Periksa hasil penyimpanan", exact: true }).click();
  await expect(page.getByRole("button", { name: "Simpan grup", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Periksa hasil penyimpanan", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Grup penerima tersimpan.");
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(3);
  expect(api.writes[2]).toEqual(api.writes[0]);
  expect(api.writes[0]?.body).toMatchObject({
    name: "Regional team",
    employmentIds: [employeeId(1), employeeId(51)],
    expectedVersion: null,
  });
  await page.getByRole("link", { name: "Lihat grup penerima", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Regional team", exact: true })).toBeVisible();
  expect(api.identity.unhandled).toEqual([]);
});
test("deactivation requires current-version review after a conflict", async ({ page }) => {
  const api = await installAudienceGroupsApi(page);
  await page.goto(`${directory}/${groupId}/edit`);
  await page.getByLabel("Name", { exact: true }).fill("Outdated team");
  await page.getByLabel("Reason", { exact: true }).fill("Deactivate team");
  api.revise();
  await page.getByRole("button", { name: "Save group", exact: true }).click();
  await expect(page.getByRole("button", { name: "Save group", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Review latest", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Leave this page?", exact: true })
    .getByRole("button", { name: "Leave page", exact: true })
    .click();
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Reviewed team");
  await expect(page.getByRole("checkbox", { name: "Active", exact: true })).not.toBeChecked();
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed inactive definition");
  await page.getByRole("button", { name: "Save group", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Audience group saved.");
  expect(api.writes.map((item) => item.body.expectedVersion)).toEqual([1, 2]);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
});
test("company replacement discards a late private group response", async ({ page }) => {
  const api = await installAudienceGroupsApi(page);
  const pending = api.holdReads();
  await page.goto(`${directory}/${groupId}`);
  await pending.start;
  try {
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page).toHaveURL(new RegExp(`company=${companyIds[1]}`));
  } finally {
    pending.release();
  }
  await expect(page.getByRole("heading", { name: "Audience groups", exact: true })).toBeVisible();
  await expect(page.getByText("Field team", { exact: true })).toHaveCount(0);
  expect(api.lookups).toHaveLength(0);
});
test("denied group reads and creation do not acquire scoped data", async ({ page }) => {
  const api = await installAudienceGroupsApi(page, false);
  await page.goto(directory);
  await expect(page.getByRole("alert")).toBeVisible();
  await page.goto(`${directory}/new`);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads).toHaveLength(0);
  expect(api.lookups).toHaveLength(0);
  expect(api.writes).toHaveLength(0);
});
