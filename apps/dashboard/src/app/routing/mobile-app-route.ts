export type MobileAppRoute = Readonly<{ href: string | null }>;

/** A public handoff selects a native route, never an account, permission or command. */
export function mobileAppRoute(
  pathname: string,
  search: string,
  hash: string,
): MobileAppRoute | null {
  if (pathname !== "/app" && !pathname.startsWith("/app/")) return null;
  const invalid = { href: null };
  if (pathname.length + search.length + hash.length > 4096 || search || hash) return invalid;
  const match = /^\/app\/(dev|staging|prod)\/([A-Za-z0-9_-]+(?:\/[A-Za-z0-9_-]+)*)$/u.exec(
    pathname,
  );
  if (!match || match[0] !== pathname) return invalid;
  const [, environment, path] = match;
  if (!environment || !path) return invalid;
  const pages = ["overview", "account", "inbox", "data-sync", "pending-actions"];
  const uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
  if (
    !pages.includes(path) &&
    !new RegExp(`^accounts/${uuid}/companies/${uuid}/inbox/${uuid}$`, "u").test(path)
  )
    return invalid;
  const scheme = environment === "prod" ? "hrismobile" : `hrismobile-${environment}`;
  return { href: `${scheme}://app/${path}` };
}
