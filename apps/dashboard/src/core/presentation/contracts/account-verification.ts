import { createContext } from "react";

/** A UI recovery action; business operations are never replayed by account verification. */
export const AccountVerificationContext = createContext<(() => void) | null>(null);
