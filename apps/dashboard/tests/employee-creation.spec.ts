import { expect, type Page, test } from "@playwright/test";
import { installEmployeeCreationApi, managerId } from "./employee-creation-api";
import { companyIds } from "./identity-api";

const create = (page: Page) => page.getByRole("button", { name: "Create employee", exact: true });
const departure = (page: Page) =>
  page.getByRole("dialog", { name: "Leave this page?", exact: true });
async function open(page: Page) {
  await page.goto("/people/employees/new");
  await expect(page.getByRole("heading", { name: "New employee", exact: true })).toBeVisible();
}
async function fill(page: Page) {
  await page.getByLabel("Legal name", { exact: true }).fill("New Employee");
  await page.getByLabel("Nationality", { exact: true }).fill("id");
  await page.getByLabel("Employee number", { exact: true }).fill("new-001");
  await page.getByLabel("Start date", { exact: true }).fill("2027-01-01");
  await page.getByLabel("Reason", { exact: true }).fill("Approved onboarding");
}

test("onboarding searches bounded assignments on demand and submits the selected employment", async ({
  page,
}) => {
  const api = await installEmployeeCreationApi(page);
  await open(page);
  await fill(page);
  expect(api.reads).toHaveLength(0);
  await page.getByRole("button", { name: "Choose Branch", exact: true }).click();
  const picker = page.getByRole("dialog", { name: "Choose Branch", exact: true });
  await expect(picker.getByRole("table").getByRole("row")).toHaveCount(51);
  await picker.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(picker.getByRole("table").getByRole("row")).toHaveCount(6);
  await picker.getByRole("button", { name: "First page", exact: true }).click();
  await expect(picker.getByRole("table").getByRole("row")).toHaveCount(51);
  const readCount = api.reads.length;
  await picker.getByLabel("Name or code", { exact: true }).fill("B005");
  expect(api.reads).toHaveLength(readCount);
  await picker.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(picker.getByRole("table").getByRole("row")).toHaveCount(2);
  await picker.getByRole("button", { name: "Select: B005 · Branch 005", exact: true }).click();
  await expect(page.getByRole("button", { name: "Choose Branch", exact: true })).toBeFocused();
  for (const [label, kind] of [
    ["Department", "DEPARTMENT"],
    ["Position", "POSITION"],
    ["Cost center", "COST_CENTER"],
  ]) {
    await page.getByRole("button", { name: `Choose ${label}`, exact: true }).click();
    await page
      .getByRole("dialog", { name: `Choose ${label}`, exact: true })
      .getByRole("button", { name: `Select: ${kind} · Selected ${kind}`, exact: true })
      .click();
  }
  await page.getByRole("button", { name: "Choose Manager", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Choose Manager", exact: true })
    .getByRole("button", { name: "Select: MGR-001 · Manager One", exact: true })
    .click();
  expect(
    api.reads
      .filter((url) => url.pathname.endsWith("/employees"))
      .every((url) => url.searchParams.get("asOf") === "2027-01-01"),
  ).toBe(true);
  expect(api.reads.every((url) => url.searchParams.get("limit") === "50")).toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-onboarding-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.screenshot({
    path: "../../.work/dashboard-onboarding-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByLabel("Nama lengkap", { exact: true })).toHaveValue("New Employee");
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-onboarding-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Buat karyawan", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Karyawan dibuat.");
  expect(api.commits).toBe(1);
  expect(api.writes[0]?.body).toMatchObject({
    employeeNumber: "NEW-001",
    person: { legalName: "New Employee", birthDate: null, nationality: "ID", email: null },
    terms: {
      effectiveFrom: "2027-01-01",
      startDate: "2027-01-01",
      managerId,
      branchId: "30000000-0000-4000-8000-000000000005",
      departmentId: "31000000-0000-4000-8000-000000000001",
      positionId: "31000000-0000-4000-8000-000000000002",
      costCenterId: "31000000-0000-4000-8000-000000000003",
    },
    reason: "Approved onboarding",
  });
  expect(api.writes[0]?.body.person.id).not.toBe(api.writes[0]?.body.id);
  await page.getByRole("link", { name: "Lihat detail", exact: true }).click();
  await expect(page.getByRole("heading", { name: "New Employee", exact: true })).toBeVisible();
  await expect(page).toHaveURL(/asOf=2027-01-01/u);
});

test("contract and field validation preserve onboarding input and permit a corrected first rejection", async ({
  page,
}) => {
  const api = await installEmployeeCreationApi(page);
  await open(page);
  await fill(page);
  await page.getByRole("combobox", { name: "Contract", exact: true }).selectOption("FIXED_TERM");
  await create(page).click();
  await expect(page.getByRole("alert")).toContainText("A fixed-term contract requires an end date");
  expect(api.writes).toHaveLength(0);
  await page.getByLabel("End date", { exact: true }).fill("2027-12-31");
  api.rejectNext("invalid_person", { nationality: "invalid_country" });
  await create(page).click();
  await expect(
    page.getByText("Enter a valid two-letter country code.", { exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("Legal name", { exact: true })).toHaveValue("New Employee");
  await expect(page.getByText("PRIVATE TECHNICAL ERROR", { exact: true })).toHaveCount(0);
  await page.getByLabel("Nationality", { exact: true }).fill("sg");
  await create(page).click();
  await expect(page.getByRole("status")).toContainText("Employee created.");
  expect(api.writes).toHaveLength(2);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
  expect(api.writes[0]?.body.id).toBe(api.writes[1]?.body.id);
  expect(api.writes[1]?.body.terms.contract).toBe("FIXED_TERM");
});

test("a lost creation response survives MFA and recovers the same receipt without automatic resubmission", async ({
  page,
}) => {
  const api = await installEmployeeCreationApi(page);
  await open(page);
  await fill(page);
  api.loseNext();
  await create(page).click();
  await expect(page.getByRole("status")).toContainText("The creation result is not confirmed");
  await expect(page.getByLabel("Legal name", { exact: true })).toHaveAttribute("readonly", "");
  api.identity.expireMfa();
  api.rejectNext("mfa_required");
  await page.getByRole("button", { name: "Retry creation", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(page.getByLabel("Legal name", { exact: true })).toHaveValue("New Employee");
  expect(api.writes).toHaveLength(2);
  await page.getByRole("button", { name: "Retry creation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Employee created.");
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
  api.failDetails("connection_unavailable");
  await page.getByRole("link", { name: "View details", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Unable to connect");
  expect(api.writes).toHaveLength(3);
});

test("dirty onboarding protects route and company departure and clears the former draft", async ({
  page,
}) => {
  const api = await installEmployeeCreationApi(page);
  await page.goto("/people/employees");
  await page.getByRole("link", { name: "Create employee", exact: true }).click();
  await fill(page);
  await page.goBack();
  await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(page.getByLabel("Legal name", { exact: true })).toHaveValue("New Employee");
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Employees", exact: true })).toBeVisible();
  await page.getByRole("link", { name: "Create employee", exact: true }).click();
  await expect(page.getByLabel("Legal name", { exact: true })).toHaveValue("");
  expect(api.writes).toHaveLength(0);
});

test("creation, directory, and assignment permissions remain independent", async ({ page }) => {
  const api = await installEmployeeCreationApi(page, ["people.read"]);
  await open(page);
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  await expect(page.getByLabel("Legal name", { exact: true })).toHaveCount(0);
  expect(api.reads).toHaveLength(0);
  api.identity.setPermissions(["people.manage"]);
  await page.reload();
  await fill(page);
  await expect(page.getByRole("button", { name: "Choose Branch", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Choose Manager", exact: true })).toBeDisabled();
  await create(page).click();
  await expect(page.getByRole("status")).toContainText("Employee created.");
  await expect(page.getByRole("link", { name: "View details", exact: true })).toHaveCount(0);
  expect(api.commits).toBe(1);
  expect(api.reads).toHaveLength(0);
});

test("a pending creation cannot restore old company state after explicit departure", async ({
  page,
}) => {
  const api = await installEmployeeCreationApi(page);
  await open(page);
  await fill(page);
  const held = api.holdNext("save");
  try {
    await create(page).click();
    await held.entered;
    await expect(create(page)).toBeDisabled();
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
    held.release();
    await expect(page.getByRole("heading", { name: "Employees", exact: true })).toBeVisible();
    await expect(page.getByText("Employee created.", { exact: true })).toHaveCount(0);
    await page.getByRole("link", { name: "Create employee", exact: true }).click();
    await expect(page.getByLabel("Legal name", { exact: true })).toHaveValue("");
    expect(api.writes).toHaveLength(1);
    expect(api.commits).toBe(1);
  } finally {
    held.release();
  }
});

test("revoked credentials discard an uncertain onboarding without retaining private fields", async ({
  page,
}) => {
  const api = await installEmployeeCreationApi(page);
  await open(page);
  await fill(page);
  api.loseNext();
  await create(page).click();
  await expect(page.getByRole("status")).toContainText("The creation result is not confirmed");
  api.identity.revoke();
  api.rejectNext("session_revoked");
  await page.getByRole("button", { name: "Retry creation", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
  await expect(page.getByLabel("Legal name", { exact: true })).toHaveCount(0);
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(2);
});
