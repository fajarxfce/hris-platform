import { useState } from "react";
import { useLocation } from "react-router-dom";
import type { AppNavigationSection } from "../../core/presentation/components/app-portal-shell";
import {
  activeNavigationDestination,
  filterNavigationMenu,
} from "../../core/presentation/navigation/navigation-menu";

export function useNavigationMenu(sections: readonly AppNavigationSection[], collapsed: boolean) {
  const [query, setQuery] = useState("");
  const { pathname } = useLocation();
  return {
    query,
    setQuery,
    sections: filterNavigationMenu(sections, collapsed ? "" : query),
    activeDestination: activeNavigationDestination(sections, pathname),
  };
}
