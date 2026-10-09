import { Spinner } from "@fluentui/react-components";

export function AppLoading({ label }: { label: string }) {
  return (
    <div className="app-loading">
      <Spinner size="small" label={label} />
    </div>
  );
}
