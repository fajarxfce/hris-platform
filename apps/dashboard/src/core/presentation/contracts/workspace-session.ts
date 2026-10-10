import { createContext } from "react";

export type WorkspaceSessionActions = Readonly<{
  verifyAccount: () => void;
  revalidate: () => void;
}>;
export const WorkspaceSessionContext = createContext<WorkspaceSessionActions | null>(null);
