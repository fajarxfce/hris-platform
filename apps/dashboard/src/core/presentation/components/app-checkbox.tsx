import { Checkbox, type CheckboxProps } from "@fluentui/react-components";
import { forwardRef } from "react";

export const AppCheckbox = forwardRef<HTMLInputElement, Omit<CheckboxProps, "input">>(
  function AppCheckbox(props, ref) {
    return <Checkbox {...props} input={{ ref }} />;
  },
);
