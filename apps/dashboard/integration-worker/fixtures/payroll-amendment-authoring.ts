import { type BrowserContext, expect } from "@playwright/test";
import { z } from "zod";
import { payrollAmendmentDetailsDto } from "../../src/features/payroll/data/models/payroll-amendment-dto";

/** Create a reviewed draft through the browser against the actual retained payroll. */
export async function exercisePayrollAmendmentAuthoring(input: {
  author: BrowserContext;
  company: string;
  assessment: string;
  month: string;
  basicSalary: string;
}) {
  const { author, company, assessment, month, basicSalary } = input,
    page = await author.newPage(),
    base = `/api/v1/companies/${company}/payroll`;
  try {
    await page.goto(`/payroll/amendments?company=${company}&year=${month.slice(0, 4)}`);
    await page.getByRole("link", { name: "Create amendment", exact: true }).click();
    await page
      .getByRole("table", { name: "Published basis", exact: true })
      .getByRole("button", { name: `Select month: Calculation Employee · ${month}`, exact: true })
      .click();
    await expect(page).toHaveURL(new RegExp(`/payroll/amendments/new/${assessment}`));
    await page.getByRole("button", { name: `Edit month: ${month}`, exact: true }).click();
    const panel = page.getByRole("dialog", { name: `Edit month · ${month}`, exact: true });
    await expect(
      panel.getByRole("combobox", { name: "Tax residency", exact: true }),
    ).toBeDisabled();
    await panel.getByRole("textbox", { name: "Basic salary (IDR)", exact: true }).fill(basicSalary);
    await panel.getByRole("button", { name: "Apply changes", exact: true }).click();
    await expect(panel).toHaveCount(0);
    const previewResponse = page.waitForResponse(
      (response) =>
        response.request().method() === "POST" &&
        new URL(response.url()).pathname === `${base}/payslips/${assessment}/amendment-preview`,
    );
    await page.getByRole("button", { name: "Preview calculation", exact: true }).click();
    const response = await previewResponse;
    expect(response.status()).toBe(200);
    await expect(
      page.getByRole("table", { name: "Monthly comparison", exact: true }),
    ).toContainText(month);
    await expect(
      page
        .getByRole("region", { name: "Total difference", exact: true })
        .locator(".app-property-row")
        .filter({ has: page.getByRole("term").filter({ hasText: /^Take-home pay$/ }) })
        .getByRole("definition"),
    ).toContainText("-");
    await page
      .getByRole("textbox", { name: "Amendment reason", exact: true })
      .fill("Correct verified monthly remuneration");
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({
      path: "../../.work/dashboard-payroll-amendment-authoring-real.png",
      fullPage: true,
      animations: "disabled",
    });
    const savedResponse = page.waitForResponse(
      (saved) =>
        saved.request().method() === "PUT" &&
        new URL(saved.url()).pathname.startsWith(`${base}/amendments/`),
    );
    await page.getByRole("button", { name: "Save proposal", exact: true }).click();
    const saved = await savedResponse;
    expect(saved.status()).toBe(200);
    const amendment = z.uuid().parse(new URL(saved.url()).pathname.split("/").at(-1));
    expect(saved.request().postDataJSON()).toMatchObject({
      firstAssessmentId: assessment,
      expectedVersion: null,
      corrections: [{ assessmentId: assessment, compensation: { basicSalary }, input: null }],
    });
    await expect(page.getByRole("status")).toContainText("Proposal saved.");
    await page.getByRole("link", { name: "View proposal", exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`/payroll/amendments/${amendment}.*revision=0`));
    await expect(page.getByRole("status")).toContainText("Proposal revision 0");
    await expect(page.getByRole("alert")).toHaveCount(0);
    const retainedResponse = await author.request.get(`${base}/amendments/${amendment}`);
    expect(retainedResponse.status()).toBe(200);
    const retained = payrollAmendmentDetailsDto.parse(await retainedResponse.json());
    expect(retained.amendment.summary).toMatchObject({
      id: amendment,
      version: 0,
      status: "DRAFT",
    });
    expect(retained.amendment.terms.corrections).toMatchObject([
      { assessmentId: assessment, compensation: { basicSalary } },
    ]);
    return amendment;
  } finally {
    await page.close();
  }
}
