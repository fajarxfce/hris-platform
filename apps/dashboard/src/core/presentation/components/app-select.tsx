import { Field, Select, type SelectProps } from "@fluentui/react-components";
import { forwardRef } from "react";

type Props = Omit<SelectProps, "select"> & { label: string; error?: string | undefined };

export const AppSelect = forwardRef<HTMLSelectElement, Props>(function AppSelect(
  { label, error, ...select },
  ref,
) {
  return (
    <Field
      label={label}
      validationState={error ? "error" : "none"}
      validationMessage={error ?? null}
    >
      <Select {...select} select={{ ref }} />
    </Field>
  );
});
