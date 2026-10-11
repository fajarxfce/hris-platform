import { useState, useSyncExternalStore } from "react";
import { useLocation } from "react-router-dom";
import type { AppNavigationSection } from "../../core/presentation/components/app-portal-shell";
import {
  activeNavigationDestination,
  filterNavigationMenu,
} from "../../core/presentation/navigation/navigation-menu";

export function useNavigationMenu(sections: readonly AppNavigationSection[], collapsed: boolean) {
  const [query, setQuery] = useState("");
  const { pathname } = useLocation();
  const narrow = useSyncExternalStore(
    subscribeToNavigationViewport,
    isNarrowNavigation,
    () => false,
  );
  return {
    query,
    setQuery,
    sections: filterNavigationMenu(sections, collapsed || narrow ? "" : query),
    activeDestination: activeNavigationDestination(sections, pathname),
  };
}

function isNarrowNavigation() {
  return window.matchMedia?.("(max-width: 800px)").matches ?? false;
}

function subscribeToNavigationViewport(changed: () => void) {
  const media = window.matchMedia?.("(max-width: 800px)");
  media?.addEventListener("change", changed);
  return () => media?.removeEventListener("change", changed);
}
