import { type BrowserContext, expect } from "@playwright/test";
import { z } from "zod";
import { payrollAmendmentPublicationsDto } from "../../src/features/payroll/data/models/payroll-amendment-publication-dto";

/** Exercise browser admission and recover its original acknowledgement after actual worker completion. */
export async function exercisePayrollAmendmentPublication(input: {
  publisher: BrowserContext;
  company: string;
  amendment: string;
  month: string;
  approved: Readonly<{
    amendmentVersion: number;
    reviewId: string;
    reviewVersion: number;
    approvalVersion: number;
    calculationId: string;
  }>;
}) {
  const { publisher, company, amendment, month, approved } = input,
    base = `/api/v1/companies/${company}/payroll/amendments/${amendment}`,
    page = await publisher.newPage(),
    writes: { path: string; operation: string; body: unknown }[] = [];
  await page.route(`**${base}/publications/*`, async (route) => {
    const request = route.request();
    if (request.method() !== "PUT") return route.continue();
    writes.push({
      path: new URL(request.url()).pathname,
      operation: request.headers()["idempotency-key"] ?? "",
      body: request.postDataJSON(),
    });
    if (writes.length !== 1) return route.continue();
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    return route.abort("failed");
  });
  try {
    await page.goto(
      `/payroll/amendments/${amendment}/publications?company=${company}&year=${month.slice(0, 4)}`,
    );
    await expect(page.getByRole("status")).toContainText("No publication attempts.");
    await page.getByRole("link", { name: "Publish amendment", exact: true }).click();
    await page
      .getByRole("textbox", { name: "Reason", exact: true })
      .fill("Publish independently approved correction");
    await page.getByRole("button", { name: "Publish amendment", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Retry original command", exact: true }),
    ).toBeVisible();
    expect(writes).toHaveLength(1);
    expect(writes[0]?.operation).toBeTruthy();
    expect(writes[0]?.body).toEqual({
      expectedVersion: approved.amendmentVersion,
      reviewId: approved.reviewId,
      expectedReviewVersion: approved.reviewVersion,
      expectedApprovalVersion: approved.approvalVersion,
      calculationId: approved.calculationId,
      reason: "Publish independently approved correction",
    });
    const publicationId = z.uuid().parse(writes[0]?.path.split("/").at(-1));
    await expect
      .poll(
        async () => {
          const response = await publisher.request.get(`${base}/publications`);
          expect(response.status()).toBe(200);
          const publications = payrollAmendmentPublicationsDto.parse(await response.json());
          expect(publications).toHaveLength(1);
          const result = publications[0];
          expect(result?.publication.id).toBe(publicationId);
          expect(result?.publication.failureCode).toBeNull();
          return result?.publication.status;
        },
        { timeout: 30000, intervals: [250, 500, 1000] },
      )
      .toBe("PUBLISHED");
    await page.getByRole("button", { name: "Retry original command", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Publication requested.");
    expect(writes).toHaveLength(2);
    expect(writes[1]).toEqual(writes[0]);
    await page
      .getByRole("status")
      .getByRole("link", { name: "Open publication", exact: true })
      .click();
    await expect(page).toHaveURL(new RegExp(`/publications/${publicationId}\\?company=`, "u"));
    await expect(page.getByRole("status")).toContainText("Corrected statements published.");
    await expect(page.getByRole("region", { name: "Worker progress", exact: true })).toContainText(
      "1 / 1",
    );
    await expect(page.getByRole("link", { name: "Recover publication", exact: true })).toHaveCount(
      0,
    );
    await page.screenshot({
      path: "../../.work/dashboard-payroll-amendment-publication-real.png",
      fullPage: true,
      animations: "disabled",
    });
    await page.getByRole("link", { name: "Approved proposal", exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`revision=${approved.amendmentVersion}`, "u"));
    await expect(
      page.getByRole("table", { name: "Observed assessments", exact: true }),
    ).toBeVisible();
    await page.goBack();
    await page.getByRole("link", { name: "Approved calculation", exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`revision=${approved.amendmentVersion}`, "u"));
    await expect(
      page.getByRole("table", { name: "Monthly comparison", exact: true }),
    ).toContainText(month);
    await page.goBack();
    await page.getByRole("link", { name: "Review history", exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`/amendment-reviews/${approved.reviewId}\\?`, "u"));
    await expect(page.getByRole("table", { name: "History", exact: true })).toContainText(
      "Independent correction review",
    );
    await page.getByRole("link", { name: "Amendment publications", exact: true }).click();
    await expect(
      page.getByRole("table", { name: "Amendment publications", exact: true }),
    ).toContainText("Published");
    await expect(page.getByRole("link", { name: "Publish amendment", exact: true })).toHaveCount(0);
    return publicationId;
  } finally {
    await page.close();
  }
}
