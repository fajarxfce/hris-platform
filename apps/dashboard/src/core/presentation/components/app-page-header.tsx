import { Text, typographyStyles } from "@fluentui/react-components";
import type { ReactNode, Ref } from "react";

export function AppPageHeader({
  title,
  context,
  actions,
  headingRef,
}: {
  title: string;
  context?: string;
  actions?: ReactNode;
  headingRef?: Ref<HTMLHeadingElement>;
}) {
  return (
    <header className="app-page-header">
      <div>
        {context && (
          <Text size={200} className="app-muted">
            {context}
          </Text>
        )}
        <h1 style={typographyStyles.title2} ref={headingRef} tabIndex={headingRef ? -1 : undefined}>
          {title}
        </h1>
      </div>
      {actions && <div className="app-command-bar">{actions}</div>}
    </header>
  );
}
