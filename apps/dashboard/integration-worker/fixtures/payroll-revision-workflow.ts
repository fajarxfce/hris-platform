import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { expect, type Page } from "@playwright/test";
import type { PayrollPayslipSummaryDto } from "../../src/features/payroll/data/models/payroll-payslip-dto";
import { payrollAmountView } from "../../src/features/payroll/presentation/models/payroll-policy-view";

/** Borrow the publisher's page to inspect retained statements after actual amendment publication. */
export async function exercisePayrollRevisions(input: {
  reader: Page;
  company: string;
  original: PayrollPayslipSummaryDto;
  current: PayrollPayslipSummaryDto;
}) {
  const { reader, company, original, current } = input,
    api = `/api/v1/companies/${company}/payroll/payslips/${original.id}`,
    route = `/payroll/payslips/${original.id}?company=${company}&scope=AUTHORIZED&from=${original.taxMonth}&until=${original.taxMonth}`;
  const originalResponse = await reader.request.get(
    `${api}/pdf?scope=AUTHORIZED&revision=0&language=EN`,
  );
  expect(originalResponse.status()).toBe(200);
  const originalBytes = await originalResponse.body();
  const originalHash = createHash("sha256").update(originalBytes).digest("hex");
  const currentResponse = await reader.request.get(
    `${api}/pdf?scope=AUTHORIZED&revision=${current.version}&language=EN`,
  );
  expect(currentResponse.status()).toBe(200);
  const currentHash = createHash("sha256")
    .update(await currentResponse.body())
    .digest("hex");
  expect(currentHash).not.toBe(originalHash);

  await reader.goto(route);
  const revision = reader
    .getByRole("region", { name: "Overview", exact: true })
    .getByText("Payslip revision", { exact: true })
    .locator("..")
    .locator("dd");
  await expect(revision).toHaveText(String(current.version));
  await expect(reader.getByRole("region", { name: "Take-home pay", exact: true })).toContainText(
    payrollAmountView(current.takeHome, "en"),
  );
  await reader.getByRole("link", { name: "Previous revision", exact: true }).click();
  await expect(revision).toHaveText("0");
  await expect(reader.getByRole("region", { name: "Take-home pay", exact: true })).toContainText(
    payrollAmountView(original.takeHome, "en"),
  );
  await reader.getByRole("tab", { name: "Payment history", exact: true }).click();
  await expect(reader.getByRole("region", { name: "Payment summary", exact: true })).toContainText(
    payrollAmountView(current.takeHome, "en"),
  );

  for (const [expectedRevision, expectedHash] of [
    [0, originalHash],
    [current.version, currentHash],
  ] as const) {
    if (expectedRevision !== 0) {
      await reader.getByRole("link", { name: "Latest revision", exact: true }).click();
      await expect(revision).toHaveText(String(expectedRevision));
    }
    await reader.getByLabel("PDF language", { exact: true }).selectOption("EN");
    const completed = reader.waitForEvent("download");
    await reader.getByRole("button", { name: "Download PDF", exact: true }).click();
    const download = await completed;
    expect(download.suggestedFilename()).toBe(`payslip-${original.id}-v${expectedRevision}-en.pdf`);
    const path = await download.path();
    if (!path) throw new Error("Retained revision PDF required");
    const bytes = await readFile(path);
    expect(createHash("sha256").update(bytes).digest("hex")).toBe(expectedHash);
    if (expectedRevision === 0) {
      await reader.getByRole("link", { name: "View balance and returns", exact: true }).click();
      await expect(
        reader.getByRole("region", { name: "Payment balance", exact: true }),
      ).toContainText(payrollAmountView(current.takeHome, "en"));
      await reader.getByRole("link", { name: "Payslip", exact: true }).click();
      await expect(revision).toHaveText("0");
      expect(new URL(reader.url()).searchParams.get("revision")).toBe("0");
      await reader.screenshot({
        path: "../../.work/dashboard-payslip-retained-real.png",
        fullPage: true,
        animations: "disabled",
      });
    }
  }
  expect(new URL(reader.url()).searchParams.has("revision")).toBe(false);
}
