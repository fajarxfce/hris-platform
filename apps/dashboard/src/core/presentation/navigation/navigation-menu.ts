import type { AppNavigationSection } from "../components/app-portal-shell";

/** A detail route belongs to its longest visible destination, on a path boundary. */
export function activeNavigationDestination(
  sections: readonly AppNavigationSection[],
  pathname: string,
): string | null {
  return (
    sections
      .flatMap((section) => section.items)
      .map((item) => ({ to: item.to, path: item.to.split(/[?#]/u, 1)[0] ?? item.to }))
      .filter(
        (item) =>
          pathname === item.path || (item.path !== "/" && pathname.startsWith(`${item.path}/`)),
      )
      .sort((first, second) => second.path.length - first.path.length)[0]?.to ?? null
  );
}

export function filterNavigationMenu(
  sections: readonly AppNavigationSection[],
  query: string,
): readonly AppNavigationSection[] {
  const search = query.trim().toLocaleLowerCase();
  if (!search) return sections;
  return sections
    .map((section) => ({
      ...section,
      items: section.items.filter((item) =>
        `${section.label ?? ""} ${item.label}`.toLocaleLowerCase().includes(search),
      ),
    }))
    .filter((section) => section.items.length > 0);
}
