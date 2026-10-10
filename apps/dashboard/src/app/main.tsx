import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { FetchHttpClient } from "../core/data/http/fetch-http-client";
import type { OperationId } from "../core/domain/identifiers";
import { AppErrorBoundary } from "../core/presentation/components/app-error-boundary";
import { createAdministrationFeature } from "../features/administration/di/administration-feature";
import { createIdentityFeature } from "../features/identity/di/identity-feature";
import { IdentityController } from "../features/identity/presentation/controllers/identity-controller";
import { createJobsFeature } from "../features/jobs/di/jobs-feature";
import { createPeopleFeature } from "../features/people/di/people-feature";
import { createReportingFeature } from "../features/reporting/di/reporting-feature";
import { Application } from "./application";
import "./styles.css";

const queries = new QueryClient({
  defaultOptions: {
    queries: { retry: false, staleTime: 30_000, gcTime: 120_000 },
    mutations: { retry: false },
  },
});
const http = new FetchHttpClient(undefined, { clientBuild: __HRIS_DASHBOARD_BUILD__ });
const reporting = createReportingFeature(http);
const administration = createAdministrationFeature(http);
const jobs = createJobsFeature(http);
const people = createPeopleFeature(http);
const identity = new IdentityController(
  createIdentityFeature(http),
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
          <Application
            identity={identity}
            reporting={reporting}
            administration={administration}
            jobs={jobs}
            people={people}
          />
        </AppErrorBoundary>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
