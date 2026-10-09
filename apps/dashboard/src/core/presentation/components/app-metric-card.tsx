import { Text, Title1 } from "@fluentui/react-components";

export function AppMetricCard({ label, value }: { label: string; value: string }) {
  return (
    <section className="app-metric-card" aria-label={label}>
      <Text className="app-muted">{label}</Text>
      <Title1 as="p">{value}</Title1>
    </section>
  );
}
