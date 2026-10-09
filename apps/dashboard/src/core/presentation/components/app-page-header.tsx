import { Text, Title2 } from "@fluentui/react-components";
import type { ReactNode } from "react";

export function AppPageHeader({
  title,
  context,
  actions,
}: {
  title: string;
  context?: string;
  actions?: ReactNode;
}) {
  return (
    <header className="app-page-header">
      <div>
        {context && (
          <Text size={200} className="app-muted">
            {context}
          </Text>
        )}
        <Title2 as="h1">{title}</Title2>
      </div>
      {actions && <div className="app-command-bar">{actions}</div>}
    </header>
  );
}
