import { expect, it } from "vitest";
import { attendanceFixture } from "../../di/attendance-fixture";
import { attendanceCorrectionValues } from "./attendance-form-values";

it("starts a correction from the frozen break and company time without rewriting raw timestamps", () => {
  const day = attendanceFixture().days[0];
  const entry = day?.entries[0];
  if (!day || !entry) throw new Error("Missing fixture");
  const accepted = {
    ...day,
    pendingCount: 0,
    entries: [{ ...entry, status: "ACCEPTED" as const, capturedAt: "2026-10-12T15:00:29Z" }],
  };
  const values = attendanceCorrectionValues(accepted, "Asia/Jakarta");
  expect(values.clockIn).toBe("2026-10-12T22:00");
  expect(values.breakMinutes).toBe("30");
  expect(values.absence).toBe(false);
  expect(accepted.entries[0]?.capturedAt).toBe("2026-10-12T15:00:29Z");
});
