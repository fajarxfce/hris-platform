import type { ReactNode } from "react";

export function AppPropertyList({
  title,
  items,
}: {
  title: string;
  items: readonly Readonly<{ label: string; value: ReactNode }>[];
}) {
  return (
    <section className="app-resource-panel" aria-label={title}>
      <h2>{title}</h2>
      <dl className="app-property-list">
        {items.map((item) => (
          <div key={item.label} className="app-property-row">
            <dt>{item.label}</dt>
            <dd>{item.value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}
