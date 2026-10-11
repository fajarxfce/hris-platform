import { expect, type Page, test } from "@playwright/test";
import { employmentId, employmentManagerId, replacementBranchId } from "./employment-api";
import { installEmploymentTransferApi } from "./employment-transfer-api";
import { companyIds } from "./identity-api";

const path = `/people/employees/${employmentId}/transfer?company=${companyIds[0]}`;
const confirmation = (page: Page) =>
  page.getByRole("dialog", { name: "Confirm transfer", exact: true });
async function open(page: Page) {
  await page.goto(path);
  await page
    .getByRole("combobox", { name: "Destination company", exact: true })
    .selectOption(companyIds[1]);
  await expect(page.getByLabel("Transfer date", { exact: true })).toHaveValue("2026-10-01");
  await page.getByLabel("Destination employee number", { exact: true }).fill(" south-001 ");
  await page.getByLabel("Reason", { exact: true }).fill("Approved company transfer");
}
async function confirm(page: Page) {
  await page.getByRole("button", { name: "Review transfer", exact: true }).click();
  await confirmation(page).getByRole("button", { name: "Transfer employee", exact: true }).click();
}

test("transfer uses server dates and destination assignments, preserves locale and theme edits, and exposes immutable history", async ({
  page,
}) => {
  const api = await installEmploymentTransferApi(page);
  await page.goto(`/people/employees/${employmentId}?asOf=2026-10-01`);
  await page.getByRole("link", { name: "Transfer employee", exact: true }).click();
  await page
    .getByRole("combobox", { name: "Destination company", exact: true })
    .selectOption(companyIds[1]);
  await expect(page.getByLabel("Transfer date", { exact: true })).toHaveAttribute("readonly", "");
  await expect(page.getByLabel("Destination employee number", { exact: true })).toHaveValue("");
  await expect(page.getByRole("button", { name: /^Clear /u })).toHaveCount(0);
  expect(api.reads.filter((url) => url.pathname.endsWith("/organization-units"))).toHaveLength(0);
  await page.getByRole("button", { name: "Choose Branch", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Choose Branch", exact: true })
    .getByRole("button", { name: "Select: HQ · Head Office", exact: true })
    .click();
  await expect(page.getByRole("button", { name: "Choose Branch", exact: true })).toBeFocused();
  await page.getByRole("button", { name: "Choose Manager", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Choose Manager", exact: true })
    .getByRole("button", { name: "Select: MGR-001 · Manager One", exact: true })
    .click();
  const assignmentReads = api.reads.filter(
    (url) => url.pathname.endsWith("/organization-units") || url.pathname.endsWith("/employees"),
  );
  expect(assignmentReads.length).toBeGreaterThan(0);
  expect(
    assignmentReads.every((url) => url.pathname.includes(`/companies/${companyIds[1]}/`)),
  ).toBe(true);
  expect(
    assignmentReads
      .filter((url) => url.pathname.endsWith("/employees"))
      .every((url) => url.searchParams.get("asOf") === "2026-10-01"),
  ).toBe(true);
  await page.getByLabel("Destination employee number", { exact: true }).fill(" south-001 ");
  await page.getByLabel("Reason", { exact: true }).fill("Approved company transfer");
  await page.getByRole("button", { name: "Review transfer", exact: true }).click();
  await expect(confirmation(page)).toContainText("Alya Pratama → South Company");
  expect(api.transferWrites).toHaveLength(0);
  await confirmation(page).getByRole("button", { name: "Cancel", exact: true }).click();
  await page.screenshot({
    path: "../../.work/dashboard-transfer-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Approved company transfer");
  await expect(page.getByText("HQ · Head Office", { exact: true })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-transfer-dark-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Tinjau transfer", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Konfirmasi transfer", exact: true })
    .getByRole("button", { name: "Transfer karyawan", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText("Karyawan berhasil ditransfer.");
  expect(api.transferWrites[0]?.body).toMatchObject({
    expectedVersion: 7,
    employeeNumber: "SOUTH-001",
    targetCompanyId: companyIds[1],
    reason: "Approved company transfer",
    terms: {
      startDate: "2026-10-01",
      effectiveFrom: "2026-10-01",
      branchId: replacementBranchId,
      managerId: employmentManagerId,
    },
  });
  expect(api.transfers).toHaveLength(1);
  await page.getByRole("link", { name: "Lihat riwayat transfer", exact: true }).click();
  await expect(page.getByRole("table")).toContainText("Keluar");
  await expect(page.getByRole("table")).toContainText("SOUTH · South Company");
  await expect(page.getByRole("table")).toContainText("Approved company transfer");
});

test("an uncertain transfer survives account verification and reuses the original command without automatic replay", async ({
  page,
}) => {
  const api = await installEmploymentTransferApi(page);
  await open(page);
  api.loseTransfer();
  await confirm(page);
  await expect(page.getByRole("status")).toContainText("The transfer outcome is not confirmed");
  await expect(
    page.getByRole("combobox", { name: "Destination company", exact: true }),
  ).toBeDisabled();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveAttribute("readonly", "");
  api.identity.expireMfa();
  api.rejectTransfer("mfa_required");
  await page.getByRole("button", { name: "Check transfer", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  expect(api.transferWrites).toHaveLength(2);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Approved company transfer");
  await page.getByRole("button", { name: "Check transfer", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Employee transferred.");
  expect(api.transferWrites).toHaveLength(3);
  expect(api.transfers).toHaveLength(1);
  expect(
    api.transferWrites.every(
      (write) =>
        write.operation === api.transferWrites[0]?.operation &&
        JSON.stringify(write.body) === JSON.stringify(api.transferWrites[0]?.body),
    ),
  ).toBe(true);
  expect(api.transferWrites[2]?.csrf).not.toBe(api.transferWrites[0]?.csrf);
});

test("a definite conflict needs an explicit protected reload before adopting a new source version and date", async ({
  page,
}) => {
  const api = await installEmploymentTransferApi(page);
  await open(page);
  api.advanceReview();
  await confirm(page);
  await expect(page.getByRole("alert")).toContainText("This record has changed");
  await expect(page.getByRole("button", { name: "Review transfer", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  const departure = page.getByRole("dialog", { name: "Leave this page?", exact: true });
  await departure.getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Approved company transfer");
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await departure.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Transfer date", { exact: true })).toHaveValue("2026-10-02");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Destination employee number", { exact: true }).fill("SOUTH-001");
  await page.getByLabel("Reason", { exact: true }).fill("Rechecked transfer");
  await confirm(page);
  await expect(page.getByRole("status")).toContainText("Employee transferred.");
  expect(api.transferWrites.map((write) => write.body.expectedVersion)).toEqual([7, 9]);
  expect(api.transferWrites[1]?.body.terms.startDate).toBe("2026-10-02");
});

test("destination access failure preserves the source workspace and target picker grants remain separate", async ({
  page,
}) => {
  const api = await installEmploymentTransferApi(page);
  api.failReview("company_access_denied");
  await page.goto(path);
  await page
    .getByRole("combobox", { name: "Destination company", exact: true })
    .selectOption(companyIds[1]);
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Transfer employee", exact: true })).toBeVisible();
  expect(api.identity.reads.access).toBe(1);
  api.failReview(null);
  api.targetPermissions(["people.manage", "people.transfer"]);
  await page.getByRole("button", { name: "Retry", exact: true }).click();
  await expect(page.getByRole("button", { name: "Choose Branch", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Choose Manager", exact: true })).toBeDisabled();
  expect(api.transferWrites).toHaveLength(0);
});

test("clearing the destination cancels an unfinished review and prevents late restoration", async ({
  page,
}) => {
  const api = await installEmploymentTransferApi(page);
  const held = api.holdReview();
  try {
    await page.goto(path);
    await page
      .getByRole("combobox", { name: "Destination company", exact: true })
      .selectOption(companyIds[1]);
    await held.entered;
    await page.getByRole("combobox", { name: "Destination company", exact: true }).selectOption("");
    held.release();
    await expect(page.getByLabel("Transfer date", { exact: true })).toHaveCount(0);
    await expect(
      page.getByRole("combobox", { name: "Destination company", exact: true }),
    ).toHaveValue("");
    expect(api.transferWrites).toHaveLength(0);
  } finally {
    held.release();
  }
});

test("direct transfer links require source transfer access and foreign company URLs are redirected before acquisition", async ({
  page,
}) => {
  const api = await installEmploymentTransferApi(page, ["people.read", "people.manage"]);
  await page.goto(path);
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  expect(api.transferReads).toHaveLength(0);
  await page.goto(`/people/employees/${employmentId}?asOf=2026-10-01`);
  await expect(page.getByRole("link", { name: "Transfer employee", exact: true })).toHaveCount(0);
  await page.goto(path.replace(companyIds[0], companyIds[1]));
  await expect(page.getByRole("heading", { name: "Employees", exact: true })).toBeVisible();
  expect(api.transferReads).toHaveLength(0);
});

test("a pending confirmation retains keyboard focus without accepting another command or dismissal", async ({
  page,
}) => {
  const api = await installEmploymentTransferApi(page);
  await open(page);
  const held = api.holdTransfer();
  try {
    await confirm(page);
    await held.entered;
    const dialog = confirmation(page);
    const button = dialog.getByRole("button", { name: "Transfer employee", exact: true });
    await expect(dialog).toHaveAttribute("aria-busy", "true");
    await expect(button).toBeDisabled();
    await button.focus();
    await expect(button).toBeFocused();
    await button.press("Enter");
    await page.keyboard.press("Escape");
    await expect(dialog).toBeVisible();
    expect(api.transferWrites).toHaveLength(1);
    held.release();
    await expect(page.getByRole("status")).toContainText("Employee transferred.");
  } finally {
    held.release();
  }
});
