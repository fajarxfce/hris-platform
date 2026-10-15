import { type BrowserContext, expect } from "@playwright/test";
/** Review the real retained draft and calculation without ordinary payroll-read authority. */
export async function exercisePayrollAmendmentRead(input: {
  author: BrowserContext;
  company: string;
  amendment: string;
  month: string;
}) {
  const { author, company, amendment, month } = input,
    page = await author.newPage();
  try {
    await page.goto(`/payroll/amendments?company=${company}&year=${month.slice(0, 4)}`);
    const directory = page.getByRole("table", { name: "Payroll amendments", exact: true });
    await expect(directory).toContainText("Calculation Employee");
    await expect(page.getByRole("link", { name: "Payslips", exact: true })).toHaveCount(0);
    await directory.getByRole("button", { name: /^View amendment: Calculation Employee/ }).click();
    await expect(page).toHaveURL(new RegExp(`/payroll/amendments/${amendment}`));
    await expect(page.getByRole("region", { name: "Summary", exact: true })).toContainText(
      "Calculated",
    );
    await page.getByRole("tab", { name: "Proposal", exact: true }).click();
    await page
      .getByRole("tabpanel", { name: "Proposal", exact: true })
      .getByText(month, { exact: true })
      .click();
    await expect(page.getByRole("region", { name: "Earnings", exact: true })).toContainText(
      "Basic salary",
    );
    await page.getByRole("tab", { name: "History", exact: true }).click();
    const history = page.getByRole("table", { name: "History", exact: true });
    await expect(history.getByRole("row")).toHaveCount(3);
    await history
      .getByRole("button", { name: "View revision: Proposal revision 0", exact: true })
      .click();
    await page.getByRole("tab", { name: "Calculation", exact: true }).click();
    await expect(page.getByRole("tabpanel").getByRole("status")).toContainText(
      "No calculation was retained",
    );
    await page.getByRole("link", { name: "Current proposal", exact: true }).click();
    await expect(
      page.getByRole("table", { name: "Monthly comparison", exact: true }),
    ).toContainText(month);
    await expect(page.getByRole("table", { name: "Amounts", exact: true })).toContainText(
      "Take-home pay",
    );
    await expect(page.getByRole("alert")).toHaveCount(0);
    await page.screenshot({
      path: "../../.work/dashboard-payroll-amendment-real.png",
      fullPage: true,
      animations: "disabled",
    });
  } finally {
    await page.close();
  }
}
