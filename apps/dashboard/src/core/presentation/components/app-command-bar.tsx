import type { ReactNode } from "react";

export function AppCommandBar({ label, children }: { label: string; children: ReactNode }) {
  return (
    <fieldset className="app-command-bar app-command-bar-panel">
      <legend className="app-visually-hidden">{label}</legend>
      {children}
    </fieldset>
  );
}
