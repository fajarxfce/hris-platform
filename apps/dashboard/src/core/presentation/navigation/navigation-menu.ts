import type { AppNavigationSection } from "../components/app-portal-shell";

/** A detail route belongs to its longest visible destination, on a path boundary. */
export function activeNavigationDestination(
  sections: readonly AppNavigationSection[],
  pathname: string,
): string | null {
  return (
    sections
      .flatMap((section) => section.items)
      .filter(
        (item) => pathname === item.to || (item.to !== "/" && pathname.startsWith(`${item.to}/`)),
      )
      .sort((first, second) => second.to.length - first.to.length)[0]?.to ?? null
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
