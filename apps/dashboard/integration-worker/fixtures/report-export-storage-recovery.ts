import { randomUUID } from "node:crypto";
import { readFile, rename, writeFile } from "node:fs/promises";
import { expect, type Page } from "@playwright/test";
import { reportExportDto } from "../../src/features/reporting/data/models/report-export-dto";

const folder = "../../.work/worker-integration";
export async function setStorageFault(mode: "pass" | "drop-second-put") {
  const path = `${folder}/storage-fault-command.json`;
  // Only the owned fixture can create this private control file.
  await readFile(path, "utf8");
  await writeFile(`${path}.next`, JSON.stringify({ id: randomUUID(), mode }), { mode: 0o600 });
  await rename(`${path}.next`, path);
}

export async function recoverStorageInterruptedExport(
  reader: Page,
  resource: string,
  company: string,
  id: string,
  sql: (query: string) => string,
) {
  const read = async () => {
    const response = await reader.request.get(resource);
    expect(response.status()).toBe(200);
    return reportExportDto.parse(await response.json());
  };
  await expect
    .poll(
      async () =>
        JSON.parse(await readFile(`${folder}/storage-fault-status.json`, "utf8")).droppedResponses,
      { timeout: 30_000, intervals: [100, 250, 500] },
    )
    .toBe(1);
  expect(await read()).toMatchObject({ attempt: 1, completedRows: 500, status: "RENDERING" });
  expect((await reader.request.get(`${resource}/content`)).status()).not.toBe(200);
  const firstPart = sql(
    `SELECT upload_id::text FROM report_export_parts WHERE company_id='${company}' AND export_id='${id}' ORDER BY part_number`,
  );
  expect(firstPart).toMatch(/^[0-9a-f-]{36}$/u);
  const uploads = JSON.parse(
    sql(
      `SELECT json_agg(json_build_object('id',id,'sha',sha256,'bytes',byte_count) ORDER BY byte_offset) FROM report_export_uploads WHERE company_id='${company}' AND export_id='${id}'`,
    ),
  ) as { id: string; sha: string; bytes: number }[];
  expect(uploads.length).toBeGreaterThanOrEqual(2);
  const dropped = uploads.find((upload) => upload.id !== firstPart);
  if (!dropped) throw new Error("Expected a retained unconfirmed upload");
  const fault = JSON.parse(await readFile(`${folder}/storage-fault-status.json`, "utf8"));
  expect(fault).toMatchObject({
    mode: "blocked",
    droppedResponses: 1,
    lastDroppedBytes: dropped.bytes,
    lastDroppedSha256: dropped.sha,
    overCapacity: 0,
  });
  await reader.getByRole("button", { name: "Refresh", exact: true }).click();
  // Transient provider errors retain bounded job retries. The owner explicitly
  // stops the unavailable run before requesting a new export attempt.
  await reader.getByRole("button", { name: "Cancel export", exact: true }).click();
  await reader
    .getByRole("dialog")
    .getByRole("button", { name: "Cancel export", exact: true })
    .click();
  await expect
    .poll(async () => (await read()).jobStatus, { timeout: 30_000, intervals: [250, 500, 1000] })
    .toBe("CANCELLED");
  const stopped = await read();
  expect(stopped).toMatchObject({ attempt: 1, completedRows: 500, status: "STOPPED" });
  await reader.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(reader.getByRole("region", { name: "Status", exact: true })).toContainText(
    "Stopped",
  );
  await expect(reader.getByRole("button", { name: "Download CSV", exact: true })).toHaveCount(0);
  await reader.screenshot({
    path: "../../.work/report-export-storage-stopped.png",
    fullPage: true,
  });
  await setStorageFault("pass");
  const resumes: { operation: string | undefined; input: string | null; receipt: unknown }[] = [];
  const route = `**${resource}/resume`;
  await reader.route(route, async (intercept) => {
    const response = await intercept.fetch();
    expect(response.status()).toBe(200);
    resumes.push({
      operation: intercept.request().headers()["idempotency-key"],
      input: intercept.request().postData(),
      receipt: await response.json(),
    });
    if (resumes.length === 1) return intercept.abort("failed");
    return intercept.fulfill({ response });
  });
  try {
    await reader.getByRole("button", { name: "Resume export", exact: true }).click();
    await reader
      .getByRole("dialog")
      .getByRole("button", { name: "Resume export", exact: true })
      .click();
    await reader.getByRole("button", { name: "Retry original command", exact: true }).click();
    await expect.poll(() => resumes.length).toBe(2);
    expect(resumes[0]?.operation).toMatch(/^[0-9a-f-]{36}$/u);
    expect(resumes[1]).toEqual(resumes[0]);
    await expect
      .poll(async () => (await read()).status, { timeout: 60_000, intervals: [250, 500, 1000] })
      .toBe("READY");
  } finally {
    await reader.unroute(route);
  }
  const recovered = await read();
  expect(recovered).toMatchObject({
    attempt: 2,
    completedRows: 1201,
    jobStatus: "SUCCEEDED",
    expiresAt: stopped.expiresAt,
  });
  expect(recovered.jobId).not.toBe(stopped.jobId);
  return { firstPart, droppedResponse: fault.droppedResponses, resumedAttempt: recovered.attempt };
}
