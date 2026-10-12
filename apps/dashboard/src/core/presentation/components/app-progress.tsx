import { Field, ProgressBar } from "@fluentui/react-components";

export function AppProgress(props: { label: string; value: number }) {
  return (
    <Field label={props.label}>
      <ProgressBar aria-label={props.label} value={props.value} max={1} />
    </Field>
  );
}
