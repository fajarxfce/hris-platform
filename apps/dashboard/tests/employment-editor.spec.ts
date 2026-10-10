import { expect, type Page, test } from "@playwright/test";
import {
  employmentId,
  employmentManagerId,
  installEmploymentApi,
  replacementBranchId,
} from "./employment-api";
import { companyIds } from "./identity-api";

const employeePath = `/people/employees/${employmentId}`;
const editPath = `${employeePath}/employment/edit?company=${companyIds[0]}&asOf=2026-10-01`;
const save = (page: Page) => page.getByRole("button", { name: "Save revision", exact: true });
const departure = (page: Page) =>
  page.getByRole("dialog", { name: "Leave this page?", exact: true });
async function edit(page: Page) {
  await page.goto(editPath);
  await expect(page.getByLabel("Start date", { exact: true })).toHaveValue("2026-01-01");
  await page.getByLabel("Effective from", { exact: true }).fill("2027-01-01");
  await page.getByLabel("Reason", { exact: true }).fill("Approved reassignment");
}

test("employment edits retain historical assignments, choose eligible replacements and preserve dirty values across locale and theme changes", async ({
  page,
}) => {
  const api = await installEmploymentApi(page);
  await page.goto(`${employeePath}?asOf=2026-10-01`);
  await page.getByRole("link", { name: "Edit employment", exact: true }).click();
  await expect(page.getByLabel("Start date", { exact: true })).toHaveAttribute("readonly", "");
  await expect(page.getByLabel("Effective from", { exact: true })).toHaveValue("2026-10-01");
  await expect(page.getByText("OLD · Former branch (Inactive)", { exact: true })).toBeVisible();
  await expect(page.getByText("Reference unavailable", { exact: true })).toBeVisible();
  expect(api.reads.filter((url) => url.pathname.endsWith("/organization-units"))).toHaveLength(0);
  await page.getByLabel("Effective from", { exact: true }).fill("2027-01-01");
  await page.getByLabel("Reason", { exact: true }).fill("Approved reassignment");
  await page.getByRole("button", { name: "Clear Position", exact: true }).click();
  await page.getByRole("button", { name: "Choose Branch", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Choose Branch", exact: true })
    .getByRole("button", { name: "Select: HQ · Head Office", exact: true })
    .click();
  await expect(page.getByRole("button", { name: "Choose Branch", exact: true })).toBeFocused();
  await page.getByRole("button", { name: "Choose Manager", exact: true }).click();
  const picker = page.getByRole("dialog", { name: "Choose Manager", exact: true });
  await expect(picker.getByRole("table").getByRole("row")).toHaveCount(2);
  await expect(picker.getByRole("button", { name: /Select: EMP-001/u })).toHaveCount(0);
  await picker.getByRole("button", { name: "Select: MGR-001 · Manager One", exact: true }).click();
  expect(
    api.reads
      .filter((url) => url.pathname.endsWith("/employees"))
      .every((url) => url.searchParams.get("asOf") === "2027-01-01"),
  ).toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-employment-editor-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.screenshot({
    path: "../../.work/dashboard-employment-editor-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Approved reassignment");
  await expect(page.getByLabel("Berlaku sejak", { exact: true })).toHaveValue("2027-01-01");
  await expect(page.getByText("HQ · Head Office", { exact: true })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-employment-editor-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Simpan revisi", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Revisi employment tersimpan.");
  expect(api.writes[0]?.body).toMatchObject({
    version: 7,
    terms: {
      startDate: "2026-01-01",
      effectiveFrom: "2027-01-01",
      branchId: replacementBranchId,
      positionId: null,
      managerId: employmentManagerId,
    },
    reason: "Approved reassignment",
  });
  expect(api.commits).toBe(1);
  await page.getByRole("link", { name: "Lihat detail", exact: true }).click();
  await expect(page).toHaveURL(/asOf=2027-01-01/u);
  await expect(
    page
      .getByRole("region", { name: "Employment", exact: true })
      .locator(".app-property-row")
      .filter({ hasText: "Revisi berlaku" }),
  ).toContainText("8");
});

test("invalid employment terms and a definite server rejection leave the form editable without hidden retries", async ({
  page,
}) => {
  const api = await installEmploymentApi(page);
  await edit(page);
  await page.getByRole("combobox", { name: "Contract", exact: true }).selectOption("FIXED_TERM");
  await save(page).click();
  await expect(page.getByRole("alert")).toContainText("A fixed-term contract requires an end date");
  expect(api.writes).toHaveLength(0);
  await page.getByLabel("End date", { exact: true }).fill("2027-12-31");
  api.rejectNext("organization_assignment_unavailable");
  await save(page).click();
  await expect(page.getByRole("alert")).toContainText(
    "An organization assignment is no longer available",
  );
  await expect(page.getByText("PRIVATE TECHNICAL ERROR", { exact: true })).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Approved reassignment");
  await page.getByRole("button", { name: "Clear Branch", exact: true }).click();
  await save(page).click();
  await expect(page.getByRole("status")).toContainText("Employment revision saved.");
  expect(api.writes).toHaveLength(2);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
  expect(api.writes[1]?.body.terms.branchId).toBeNull();
});

test("an uncertain employment revision survives MFA and recovers its original receipt without resubmitting automatically", async ({
  page,
}) => {
  const api = await installEmploymentApi(page);
  await edit(page);
  api.loseNext();
  await save(page).click();
  await expect(page.getByRole("status")).toContainText("The revision result is not confirmed");
  await expect(page.getByLabel("Effective from", { exact: true })).toHaveAttribute("readonly", "");
  api.identity.expireMfa();
  api.rejectNext("mfa_required");
  await page.getByRole("button", { name: "Retry revision", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Approved reassignment");
  expect(api.writes).toHaveLength(2);
  await page.getByRole("button", { name: "Retry revision", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Employment revision saved.");
  expect(api.writes).toHaveLength(3);
  expect(api.commits).toBe(1);
  expect(
    api.writes.every(
      (write) =>
        write.operation === api.writes[0]?.operation &&
        JSON.stringify(write.body) === JSON.stringify(api.writes[0]?.body),
    ),
  ).toBe(true);
  expect(api.writes[2]?.csrf).not.toBe(api.writes[0]?.csrf);
  api.failReads("connection_unavailable");
  await page.getByRole("link", { name: "View details", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Unable to connect");
  expect(api.writes).toHaveLength(3);
});

test("a definite version conflict requires a protected reload and cannot overwrite from stale employment details", async ({
  page,
}) => {
  const api = await installEmploymentApi(page);
  await edit(page);
  api.advanceVersion();
  await save(page).click();
  await expect(page.getByRole("alert")).toContainText("This record has changed");
  await expect(save(page)).toBeDisabled();
  await page.getByRole("button", { name: "Reload employment", exact: true }).click();
  await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Approved reassignment");
  await page.getByRole("button", { name: "Reload employment", exact: true }).click();
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await expect(page.getByLabel("Effective from", { exact: true })).toHaveValue("2026-10-01");
  await page.getByLabel("Reason", { exact: true }).fill("Revised after review");
  await save(page).click();
  await expect(page.getByRole("status")).toContainText("Employment revision saved.");
  expect(api.writes.map((write) => write.body.version)).toEqual([7, 9]);
  expect(api.commits).toBe(1);
});

test("direct employment editor links require full read and management while organization browsing remains independent", async ({
  page,
}) => {
  const api = await installEmploymentApi(page, ["people.manage"]);
  await page.goto(editPath);
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  expect(api.reads).toHaveLength(0);
  api.identity.setPermissions(["people.read"]);
  await page.goto(`${employeePath}?asOf=2026-10-01`);
  await expect(page.getByRole("heading", { name: "Alya Pratama", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Edit employment", exact: true })).toHaveCount(0);
  api.identity.setPermissions(["people.read", "people.manage"]);
  await edit(page);
  await expect(page.getByRole("button", { name: "Choose Branch", exact: true })).toBeDisabled();
  await expect(page.getByText("OLD · Former branch (Inactive)", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Choose Manager", exact: true })).toBeEnabled();
  expect(api.writes).toHaveLength(0);
});

test("company departure cancels a pending revision and prevents its late result from restoring the old editor", async ({
  page,
}) => {
  const api = await installEmploymentApi(page);
  await edit(page);
  const held = api.holdNext("save");
  try {
    await save(page).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
    held.release();
    await expect(page.getByRole("heading", { name: "Employees", exact: true })).toBeVisible();
    await expect(page.getByRole("table")).toContainText("Bayu Selatan");
    await expect(page.getByText("Employment revision saved.", { exact: true })).toHaveCount(0);
    await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
    expect(api.writes).toHaveLength(1);
  } finally {
    held.release();
  }
});

test("revoked credentials discard the uncertain employment form and its private assignments", async ({
  page,
}) => {
  const api = await installEmploymentApi(page);
  await edit(page);
  api.loseNext();
  await save(page).click();
  await expect(page.getByRole("status")).toContainText("The revision result is not confirmed");
  api.identity.revoke();
  api.rejectNext("session_revoked");
  await page.getByRole("button", { name: "Retry revision", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
  await expect(page.getByText("OLD · Former branch (Inactive)", { exact: true })).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(2);
});
