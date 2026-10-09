import { Field, Input, type InputProps } from "@fluentui/react-components";
import { forwardRef } from "react";

type Props = Omit<InputProps, "input"> & {
  label: string;
  error?: string | undefined;
  hint?: string | undefined;
};

export const AppTextField = forwardRef<HTMLInputElement, Props>(function AppTextField(
  { label, error, hint, ...input },
  ref,
) {
  return (
    <Field
      label={label}
      validationState={error ? "error" : "none"}
      validationMessage={error ?? null}
      hint={hint ?? null}
    >
      <Input {...input} input={{ ref }} />
    </Field>
  );
});
