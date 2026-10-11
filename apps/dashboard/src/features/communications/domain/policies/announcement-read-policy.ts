import { failed, type Result, success } from "../../../../core/domain/result";

export const canManageAnnouncements = (permissions: readonly string[]): boolean =>
  permissions.includes("announcements.manage");

export function parseAnnouncementVersion(value: string | null): Result<number | null> {
  if (value === null) return success(null);
  if (!/^(0|[1-9][0-9]{0,15})$/u.test(value) || !Number.isSafeInteger(Number(value)))
    return failed("invalid_page");
  return success(Number(value));
}
