import type { CompanyId } from "../../../../core/domain/identifiers";

export const announcementsPath = "/communications/announcements";
export function announcementParameters(
  company: CompanyId,
  after: string | null = null,
  revision: string | null = null,
) {
  const parameters = new URLSearchParams({ company });
  if (after !== null) parameters.set("after", after);
  if (revision !== null) parameters.set("revision", revision);
  return parameters;
}
