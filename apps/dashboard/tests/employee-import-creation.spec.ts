import { readFile } from "node:fs/promises";
import { expect, type Page, test } from "@playwright/test";
import { employeeImportPermissions } from "./employee-import-api";
import {
  employeeCsv,
  employeeCsvTemplate,
  installEmployeeImportCreationApi,
} from "./employee-import-creation-api";
import { companyIds } from "./identity-api";

const route = `/people/imports/new?company=${companyIds[0]}`;
async function choose(page: Page, name = "employees.csv", buffer = Buffer.from(employeeCsv)) {
  const chosen = page.waitForEvent("filechooser");
  await page.getByRole("button", { name: "Choose CSV", exact: true }).click();
  await (await chosen).setFiles({ name, mimeType: "text/csv", buffer });
}
test("CSV intake downloads the authorized template, reads a file locally and queues an explicit preview", async ({
  page,
}) => {
  const api = await installEmployeeImportCreationApi(page);
  await page.goto(`/people/imports?company=${companyIds[0]}`);
  await page.getByRole("link", { name: "Import CSV", exact: true }).click();
  await expect(page.getByRole("button", { name: "Prepare preview", exact: true })).toBeDisabled();
  const downloading = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download template", exact: true }).click();
  const download = await downloading;
  expect(download.suggestedFilename()).toBe("employee-import-template.csv");
  const downloaded = await download.path();
  if (!downloaded) throw new Error("Expected template download");
  expect(await readFile(downloaded, "utf8")).toBe(employeeCsvTemplate);
  expect(api.templates).toHaveLength(1);
  await choose(page);
  await expect(page.getByRole("region", { name: "Selected file", exact: true })).toContainText(
    "employees.csv",
  );
  await expect(page.getByRole("main")).not.toContainText("Preview, Employee");
  expect(api.starts).toEqual([]);
  await page.getByRole("button", { name: "Prepare preview", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("provide a reason");
  expect(api.starts).toEqual([]);
  await page.getByLabel("Reason", { exact: true }).fill("  Monthly intake  ");
  await page.getByRole("button", { name: "Prepare preview", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Preview queued.");
  await expect(page.getByRole("region", { name: "Selected file", exact: true })).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
  expect(api.starts[0]?.body).toMatchObject({
    fileName: "employees.csv",
    csv: employeeCsv,
    reason: "Monthly intake",
  });
  expect(api.committed).toBe(1);
  await page.getByRole("link", { name: "Open import", exact: true }).click();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "Preparing preview",
  );
  await expect(page.getByRole("link", { name: "Apply import", exact: true })).toHaveCount(0);
  expect(api.identity.unhandled).toEqual([]);
});
for (const invalid of [
  { name: "employees.txt", buffer: Buffer.from(employeeCsv), message: "Choose a .csv file" },
  { name: "empty.csv", buffer: Buffer.alloc(0), message: "must contain data" },
  { name: "large.csv", buffer: Buffer.alloc(524_289), message: "exceeds the size limit" },
  {
    name: "encoding.csv",
    buffer: Buffer.from([0xff, 0xfe, 0x41]),
    message: "Save the file as UTF-8",
  },
])
  test(`invalid input ${invalid.name} fails without starting a server import`, async ({ page }) => {
    const api = await installEmployeeImportCreationApi(page);
    await page.goto(route);
    await choose(page, invalid.name, invalid.buffer);
    await expect(page.getByRole("alert")).toContainText(invalid.message);
    await expect(page.getByRole("button", { name: "Prepare preview", exact: true })).toBeDisabled();
    expect(api.starts).toEqual([]);
    await choose(page);
    await expect(page.getByRole("region", { name: "Selected file", exact: true })).toContainText(
      "employees.csv",
    );
    await expect(page.getByRole("alert")).toHaveCount(0);
  });
test("a definite CSV format rejection allows correction with a new operation", async ({ page }) => {
  const api = await installEmployeeImportCreationApi(page);
  await page.goto(route);
  await choose(page, "bad.csv", Buffer.from("wrong,headers\n1,2"));
  await page.getByLabel("Reason", { exact: true }).fill("Intake review");
  api.reject("invalid_employee_csv");
  await page.getByRole("button", { name: "Prepare preview", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Check the CSV headers and format");
  await expect(page.getByRole("main")).not.toContainText("PRIVATE CSV DIAGNOSTIC");
  await choose(page, "corrected.csv");
  await page.getByRole("button", { name: "Prepare preview", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Preview queued.");
  expect(api.starts.map((start) => start.body.fileName)).toEqual(["bad.csv", "corrected.csv"]);
  expect(api.starts[1]?.operation).not.toBe(api.starts[0]?.operation);
  expect(api.starts[1]?.body.id).toBe(api.starts[0]?.body.id);
  expect(api.committed).toBe(1);
});
test("an uncertain preview retains the original CSV through MFA and a later format rejection", async ({
  page,
}) => {
  const api = await installEmployeeImportCreationApi(page);
  await page.goto(route);
  await choose(page);
  await page.getByLabel("Reason", { exact: true }).fill("Original CSV intake");
  api.lose();
  await page.getByRole("button", { name: "Prepare preview", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The outcome is not confirmed");
  await expect(page.getByRole("button", { name: "Choose CSV", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Remove file", exact: true })).toBeDisabled();
  api.identity.expireMfa();
  api.reject("mfa_required", 403);
  await page.getByRole("button", { name: "Retry original request", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Original CSV intake");
  await expect(page.getByRole("region", { name: "Selected file", exact: true })).toContainText(
    "employees.csv",
  );
  api.reject("invalid_employee_csv");
  await page.getByRole("button", { name: "Retry original request", exact: true }).click();
  await expect(page.getByRole("button", { name: "Choose CSV", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Retry original request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Preview queued.");
  expect(api.starts).toHaveLength(4);
  for (const start of api.starts) {
    expect(start.operation).toBe(api.starts[0]?.operation);
    expect(start.body).toEqual(api.starts[0]?.body);
  }
  expect(api.committed).toBe(1);
});
test("clearing or cancelling a replacement selection cannot submit the previous CSV", async ({
  page,
}) => {
  const api = await installEmployeeImportCreationApi(page);
  await page.goto(route);
  await choose(page);
  await page.getByRole("button", { name: "Remove file", exact: true }).click();
  await expect(page.getByRole("button", { name: "Prepare preview", exact: true })).toBeDisabled();
  await choose(page);
  const replacing = page.waitForEvent("filechooser");
  await page.getByRole("button", { name: "Choose CSV", exact: true }).click();
  await (await replacing).setFiles([]);
  await expect(page.getByRole("region", { name: "Selected file", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Prepare preview", exact: true })).toBeDisabled();
  await expect(page.getByRole("alert")).toHaveCount(0);
  expect(api.starts).toEqual([]);
});
test("scope departure disposes the CSV and ignores a late preview receipt", async ({ page }) => {
  const api = await installEmployeeImportCreationApi(page);
  await page.goto(route);
  await choose(page, "north-intake.csv");
  await page.getByLabel("Reason", { exact: true }).fill("North intake");
  const pending = api.holdStart();
  await page.getByRole("button", { name: "Prepare preview", exact: true }).click();
  await pending.entered;
  try {
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await page
      .getByRole("dialog", { name: "Leave this page?", exact: true })
      .getByRole("button", { name: "Leave page", exact: true })
      .click();
    await expect(page).toHaveURL(`/people/imports?company=${companyIds[1]}`);
  } finally {
    pending.release();
  }
  await expect(page.getByRole("table")).toContainText("south-1.csv");
  await expect(page.getByRole("main")).not.toContainText("north-intake.csv");
  await expect(page.getByText("Preview queued.", { exact: true })).toHaveCount(0);
  expect(api.starts).toHaveLength(1);
});
test("expanded JSON is a correctable size rejection rather than an uncertain request", async ({
  page,
}) => {
  const api = await installEmployeeImportCreationApi(page);
  await page.goto(route);
  await choose(page, "expanded.csv", Buffer.alloc(200_000));
  await page.getByLabel("Reason", { exact: true }).fill("Intake");
  await page.getByRole("button", { name: "Prepare preview", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("The encoded request exceeds");
  await expect(page.getByRole("button", { name: "Choose CSV", exact: true })).toBeEnabled();
  expect(api.starts).toEqual([]);
  await choose(page);
  await page.getByRole("button", { name: "Prepare preview", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Preview queued.");
  expect(api.committed).toBe(1);
});
test("localized dark mobile intake retains the file and reason across preferences", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await installEmployeeImportCreationApi(page);
  await page.goto(route);
  const name = `${"x".repeat(116)}.csv`;
  await choose(page, name);
  await page.getByLabel("Reason", { exact: true }).fill("Import bulanan");
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Import bulanan");
  await expect(page.getByRole("region", { name: "File terpilih", exact: true })).toContainText(
    name,
  );
  await expect(page.getByRole("button", { name: "Siapkan preview", exact: true })).toBeEnabled();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-employee-import-creation-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
});
for (const missing of employeeImportPermissions)
  test(`CSV creation requires ${missing} before opening files`, async ({ page }) => {
    const api = await installEmployeeImportCreationApi(
      page,
      employeeImportPermissions.filter((p) => p !== missing),
    );
    await page.goto(route);
    await expect(page.getByRole("alert")).toContainText(
      "You do not have access to employee imports",
    );
    await expect(page.getByRole("button", { name: "Choose CSV", exact: true })).toHaveCount(0);
    expect(api.starts).toEqual([]);
    expect(api.templates).toEqual([]);
    expect(api.reads).toEqual([]);
  });
