import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { FetchHttpClient } from "../core/data/http/fetch-http-client";
import type { OperationId } from "../core/domain/identifiers";
import { AppErrorBoundary } from "../core/presentation/components/app-error-boundary";
import { createIdentityFeature } from "../features/identity/di/identity-feature";
import { IdentityController } from "../features/identity/presentation/controllers/identity-controller";
import { Application } from "./application";
import "./styles.css";

const queries = new QueryClient({
  defaultOptions: {
    queries: { retry: false, staleTime: 30_000, gcTime: 120_000 },
    mutations: { retry: false },
  },
});
const identity = new IdentityController(
  createIdentityFeature(new FetchHttpClient()),
  () => crypto.randomUUID() as OperationId,
  () => queries.clear(),
);
const root = document.getElementById("root");
if (!root) throw new Error("Application root is missing");
createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queries}>
      <BrowserRouter>
        <AppErrorBoundary locale="en" onReload={() => window.location.reload()}>
          <Application identity={identity} />
        </AppErrorBoundary>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
